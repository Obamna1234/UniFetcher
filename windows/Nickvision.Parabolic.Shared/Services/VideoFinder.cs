using Nickvision.Parabolic.Shared.Helpers;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

namespace Nickvision.Parabolic.Shared.Services;

/// <summary>A video stream the video finder saw a page's player load.</summary>
/// <param name="Page">The page the video is on.</param>
/// <param name="Stream">The stream's own link (HLS, DASH or a file).</param>
/// <param name="Title">The page's title, used as the video's name.</param>
/// <param name="CookieFile">The built-in browser's cookies for yt-dlp (Netscape format), or null.</param>
/// <param name="UserAgent">The built-in browser's identity, so the site sees the same browser.</param>
public record FoundVideo(Uri Page, Uri Stream, StreamPicker.Kind Kind, string? Title, string? CookieFile, string? UserAgent);

/// <summary>
/// Video finder: for pages yt-dlp can't read, opens the page in a hidden built-in browser (WebView2), lets the page's own
/// player load and notes the video stream it asks for. Nothing is clicked or solved: if the player only starts after a tap,
/// the link becomes "Action needed" and you press play yourself in the finder window. DRM-protected streams are still refused.
/// </summary>
public interface IVideoFinder
{
    /// <summary>False when no built-in browser is available (e.g. tests, or WebView2 missing).</summary>
    bool IsAvailable { get; }

    /// <summary>Looks for the main video on the page; a video you played in the finder window is used first. Null if none was seen.</summary>
    Task<FoundVideo?> FindAsync(Uri page, CancellationToken cancellationToken = default);

    /// <summary>The finder details for a stream link (so its download uses the same referer, cookies and identity), or null.</summary>
    FoundVideo? ForStream(Uri stream);

    /// <summary>Remembers a video you played in the finder window, for the page it's on.</summary>
    void Remember(FoundVideo found);

    /// <summary>Forgets found videos and deletes the finder's saved cookies; with <paramref name="alsoBrowserData"/>, its browser data too.</summary>
    void Forget(bool alsoBrowserData);
}

/// <summary>Shared bookkeeping for video finders.</summary>
public abstract class VideoFinderBase : IVideoFinder
{
    private readonly ConcurrentDictionary<string, FoundVideo> _byPage = new();
    private readonly ConcurrentDictionary<string, FoundVideo> _byStream = new();

    public abstract bool IsAvailable { get; }

    public async Task<FoundVideo?> FindAsync(Uri page, CancellationToken cancellationToken = default)
    {
        if (_byPage.TryGetValue(Key(page), out var remembered))
        {
            return remembered;
        }
        var found = await LookAsync(page, cancellationToken);
        if (found is not null)
        {
            _byStream[Key(found.Stream)] = found;
        }
        return found;
    }

    public FoundVideo? ForStream(Uri stream) => _byStream.TryGetValue(Key(stream), out var found) ? found : null;

    public void Remember(FoundVideo found)
    {
        _byPage[Key(found.Page)] = found;
        _byStream[Key(found.Stream)] = found;
    }

    public virtual void Forget(bool alsoBrowserData)
    {
        _byPage.Clear();
        _byStream.Clear();
    }

    protected abstract Task<FoundVideo?> LookAsync(Uri page, CancellationToken cancellationToken);

    private static string Key(Uri url) => url.GetLeftPart(UriPartial.Query).TrimEnd('/');
}

/// <summary>Used when there's no built-in browser: the video finder is off.</summary>
public class NullVideoFinder : VideoFinderBase
{
    public override bool IsAvailable => false;

    protected override Task<FoundVideo?> LookAsync(Uri page, CancellationToken cancellationToken) => Task.FromResult<FoundVideo?>(null);
}

/// <summary>Writes browser cookies in the Netscape format yt-dlp reads.</summary>
public static class NetscapeCookies
{
    public record Cookie(string Domain, string Path, bool Secure, long ExpiresUnix, string Name, string Value);

    public static string Format(IEnumerable<Cookie> cookies)
    {
        var sb = new System.Text.StringBuilder("# Netscape HTTP Cookie File\n");
        foreach (var c in cookies)
        {
            var domain = c.Domain;
            var sub = domain.StartsWith('.') ? "TRUE" : "FALSE";
            sb.Append(domain).Append('\t').Append(sub).Append('\t').Append(string.IsNullOrEmpty(c.Path) ? "/" : c.Path).Append('\t')
              .Append(c.Secure ? "TRUE" : "FALSE").Append('\t').Append(Math.Max(0, c.ExpiresUnix)).Append('\t')
              .Append(c.Name).Append('\t').Append(c.Value).Append('\n');
        }
        return sb.ToString();
    }
}
