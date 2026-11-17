using Nickvision.Parabolic.Shared.Helpers;
using Nickvision.Parabolic.Shared.Models;
using Nickvision.Parabolic.Shared.Services;
using System.Net.NetworkInformation;
using Xunit;

namespace UniFetcher.Tests;

public class VideoFinderAndVpnTests
{
    private static StreamPicker.Candidate C(string url, string? type = null) => new(url, StreamPicker.Classify(url, type)!.Value);

    [Fact]
    public void RecognisesStreamsAndIgnoresPieces()
    {
        Assert.Equal(StreamPicker.Kind.Hls, StreamPicker.Classify("https://cdn.example.com/v/123/master.m3u8?token=abc"));
        Assert.Equal(StreamPicker.Kind.Hls, StreamPicker.Classify("https://cdn.example.com/play?file=video.m3u8"));
        Assert.Equal(StreamPicker.Kind.Dash, StreamPicker.Classify("https://cdn.example.com/v/manifest.mpd"));
        Assert.Equal(StreamPicker.Kind.File, StreamPicker.Classify("https://cdn.example.com/v/1080p.mp4?e=1"));
        Assert.Equal(StreamPicker.Kind.Hls, StreamPicker.Classify("https://cdn.example.com/stream/abc", "application/vnd.apple.mpegurl; charset=utf-8"));
        Assert.Null(StreamPicker.Classify("https://cdn.example.com/v/seg-12.ts"));
        Assert.Null(StreamPicker.Classify("https://cdn.example.com/v/chunk-3.m4s", "video/mp4"));
        Assert.Null(StreamPicker.Classify("https://example.com/player.js", "application/javascript"));
        Assert.Null(StreamPicker.Classify("blob:https://example.com/123"));
    }

    [Fact]
    public void PrefersTheMainHlsPlaylistOverPreviewsAndVariants()
    {
        var picked = StreamPicker.Pick([
            C("https://cdn.example.com/previews/clip1.mp4"),
            C("https://cdn.example.com/v/123/master.m3u8"),
            C("https://cdn.example.com/v/123/720p/index.m3u8")
        ]);
        Assert.Equal("https://cdn.example.com/v/123/master.m3u8", picked?.Url);
    }

    [Fact]
    public void TakesTheFirstPlaylistWhenNoneIsCalledMaster()
    {
        var picked = StreamPicker.Pick([C("https://cdn.example.com/a/playlist.m3u8"), C("https://cdn.example.com/a/1080/chunks.m3u8")]);
        Assert.Equal("https://cdn.example.com/a/playlist.m3u8", picked?.Url);
    }

    [Fact]
    public void ATrailerIsStillBetterThanNothing()
    {
        Assert.Equal("https://cdn.example.com/trailers/scene.mp4", StreamPicker.Pick([C("https://cdn.example.com/trailers/scene.mp4")])?.Url);
        Assert.Null(StreamPicker.Pick([]));
    }

    [Fact]
    public void DescribeHidesTokens()
    {
        Assert.Equal("HLS stream (cdn.example.com/…/master.m3u8)", StreamPicker.Describe(C("https://cdn.example.com/v/123/master.m3u8?token=secret")));
    }

    [Fact]
    public void VideoNotFoundNoteNeedsAction()
    {
        var log = $"{FailureReasons.VideoNotFoundNote} (it may need you to press play).\n\nERROR: Unsupported URL: https://example.com/x";
        var reason = FailureReasons.ClassifyLog(log, false);
        Assert.Equal(FailureReason.VideoNotFound, reason);
        Assert.True(reason.NeedsAction());
        // Saved as a number: must stay after "Other"
        Assert.True((int)FailureReason.VideoNotFound > (int)FailureReason.Other);
    }

    [Fact]
    public void CookiesAreWrittenForYtdlp()
    {
        var text = NetscapeCookies.Format([new NetscapeCookies.Cookie(".example.com", "/", true, 1900000000, "sid", "abc"), new NetscapeCookies.Cookie("cdn.example.com", "", false, 0, "k", "v")]);
        Assert.StartsWith("# Netscape HTTP Cookie File\n", text);
        Assert.Contains(".example.com\tTRUE\t/\tTRUE\t1900000000\tsid\tabc\n", text);
        Assert.Contains("cdn.example.com\tFALSE\t/\tFALSE\t0\tk\tv\n", text);
    }

    [Theory]
    [InlineData("Ethernet 3", "Private Internet Access Network Adapter", NetworkInterfaceType.Ethernet, true)]
    [InlineData("wgpia0", "WireGuard Tunnel", NetworkInterfaceType.Unknown, true)]
    [InlineData("OpenVPN Wintun", "Wintun Userspace Tunnel", NetworkInterfaceType.Unknown, true)]
    [InlineData("VPN Connection", "WAN Miniport (IKEv2)", NetworkInterfaceType.Ppp, true)]
    [InlineData("Wi-Fi", "Intel(R) Wi-Fi 6 AX201 160MHz", NetworkInterfaceType.Wireless80211, false)]
    [InlineData("Ethernet", "Realtek PCIe GbE Family Controller", NetworkInterfaceType.Ethernet, false)]
    [InlineData("vEthernet (WSL)", "Hyper-V Virtual Ethernet Adapter", NetworkInterfaceType.Ethernet, false)]
    [InlineData("Teredo Tunneling Pseudo-Interface", "Teredo Tunneling Pseudo-Interface", NetworkInterfaceType.Tunnel, false)]
    public void RecognisesVpnAdapters(string name, string description, NetworkInterfaceType type, bool expected)
    {
        Assert.Equal(expected, VpnService.IsVpnAdapter(name, description, type));
    }

    [Fact]
    public void RetriesAreTougher()
    {
        var args = YtdlpExecutableService.RetryArguments;
        Assert.Contains("--fragment-retries", args);
        Assert.Contains("fragment:exp=1:30", args);
    }
}
