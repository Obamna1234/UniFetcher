using System;
using System.Collections.Generic;
using System.Linq;

namespace Nickvision.Parabolic.Shared.Helpers;

/// <summary>
/// Video finder: decides which of the requests a page's player made is the actual video stream.
/// Same rules as the Android app (util/StreamPicker.kt).
/// </summary>
public static class StreamPicker
{
    public enum Kind
    {
        /// <summary>HLS playlist (.m3u8): what most players use.</summary>
        Hls,
        /// <summary>DASH manifest (.mpd).</summary>
        Dash,
        /// <summary>A plain video file (.mp4, .webm...).</summary>
        File
    }

    public record Candidate(string Url, Kind Kind);

    private static readonly string[] FileExts = [".mp4", ".webm", ".m4v", ".mov", ".mkv"];

    /// <summary>Pieces of a stream (not the stream itself) and things that aren't the video.</summary>
    private static readonly string[] IgnoredExts = [".ts", ".m4s", ".aac", ".m4a", ".vtt", ".srt", ".jpg", ".jpeg", ".png", ".webp", ".gif"];

    /// <summary>Hints that a video is a preview, advert or similar rather than the main video.</summary>
    private static readonly string[] SideWords = ["preview", "thumb", "sprite", "trailer", "teaser", "promo", "banner", "/ads/", "/ad/", "advert", "vast", "doubleclick", "googlesyndication", "init.mp4"];

    /// <summary>The kind of video request <paramref name="url"/> is (using its Content-Type if known), or null if it isn't one.</summary>
    public static Kind? Classify(string url, string? contentType = null)
    {
        var lower = url.Trim().ToLowerInvariant();
        if (!lower.StartsWith("http://") && !lower.StartsWith("https://"))
        {
            return null;
        }
        if (IsLocalAddress(lower))
        {
            return null;
        }
        var noFragment = lower.Split('#')[0];
        var path = noFragment.Split('?')[0];
        var query = noFragment.Contains('?') ? noFragment[(noFragment.IndexOf('?') + 1)..] : string.Empty;
        if (IgnoredExts.Any(path.EndsWith))
        {
            return null;
        }
        if (path.EndsWith(".m3u8") || query.Contains(".m3u8"))
        {
            return Kind.Hls;
        }
        if (path.EndsWith(".mpd") || query.Contains(".mpd"))
        {
            return Kind.Dash;
        }
        if (FileExts.Any(path.EndsWith))
        {
            return Kind.File;
        }
        var type = contentType?.Split(';')[0].Trim().ToLowerInvariant() ?? string.Empty;
        return type switch
        {
            "application/vnd.apple.mpegurl" or "application/x-mpegurl" or "audio/mpegurl" => Kind.Hls,
            "application/dash+xml" => Kind.Dash,
            "video/mp4" or "video/webm" or "video/quicktime" or "video/x-matroska" => Kind.File,
            _ => null
        };
    }

    /// <summary>
    /// True for links to this computer or the home network (localhost, 10.x, 192.168.x, 172.16-31.x, 169.254.x, .local).
    /// A web page could ask for these, but they're never the video, so the finder never downloads them.
    /// </summary>
    public static bool IsLocalAddress(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
        {
            return false;
        }
        var host = uri.Host.Trim('[', ']').ToLowerInvariant();
        if (uri.IsLoopback || host == "localhost" || host.EndsWith(".localhost") || host.EndsWith(".local"))
        {
            return true;
        }
        if (System.Net.IPAddress.TryParse(host, out var ip))
        {
            if (ip.AddressFamily == System.Net.Sockets.AddressFamily.InterNetworkV6)
            {
                return ip.IsIPv6LinkLocal || ip.IsIPv6SiteLocal || ip.IsIPv6UniqueLocal || System.Net.IPAddress.IsLoopback(ip);
            }
            var b = ip.GetAddressBytes();
            return b[0] == 127 || b[0] == 10 || b[0] == 0 || (b[0] == 192 && b[1] == 168) || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 169 && b[1] == 254);
        }
        return false;
    }

    public static bool IsSide(string url)
    {
        var u = url.ToLowerInvariant();
        return SideWords.Any(u.Contains);
    }

    /// <summary>
    /// The most likely main video, in the order the page asked for them, or null. Prefers HLS, then DASH, then plain files;
    /// avoids previews and adverts; among equals takes the first one (a player loads its main playlist before the quality variants).
    /// </summary>
    public static Candidate? Pick(IEnumerable<Candidate> candidates) => candidates
        .DistinctBy(c => c.Url)
        .Select((c, i) => (c, i))
        .OrderByDescending(x => IsSide(x.c.Url) ? 0 : 1)
        .ThenByDescending(x => x.c.Kind switch { Kind.Hls => 3, Kind.Dash => 2, _ => 1 })
        .ThenByDescending(x => x.c.Url.Contains("master", StringComparison.OrdinalIgnoreCase) ? 1 : 0)
        .ThenBy(x => x.i)
        .Select(x => x.c)
        .FirstOrDefault();

    /// <summary>Short, safe description for diagnostics: kind, host and file name, without the query.</summary>
    public static string Describe(Candidate candidate)
    {
        var noQuery = candidate.Url.Split('?')[0].Split('#')[0];
        var afterScheme = noQuery.Contains("://") ? noQuery[(noQuery.IndexOf("://") + 3)..] : noQuery;
        var host = afterScheme.Split('/')[0];
        var name = noQuery[(noQuery.LastIndexOf('/') + 1)..];
        var kind = candidate.Kind switch { Kind.Hls => "HLS", Kind.Dash => "DASH", _ => "FILE" };
        return $"{kind} stream ({host}/…/{name})";
    }
}
