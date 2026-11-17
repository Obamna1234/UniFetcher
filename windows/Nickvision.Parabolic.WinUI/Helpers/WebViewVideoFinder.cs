using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.UI.Xaml;
using Microsoft.Web.WebView2.Core;
using Nickvision.Parabolic.Shared.Helpers;
using Nickvision.Parabolic.Shared.Services;
using Nickvision.Parabolic.WinUI.Views;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace Nickvision.Parabolic.WinUI.Helpers;

/// <summary>
/// UniFetcher video finder on Windows: opens a page in a hidden WebView2 browser (its own private profile, separate from
/// your everyday browser), lets the page's player load, and notes the video stream it asks for. Pop-ups, new windows and
/// page-started downloads are blocked; nothing is clicked or solved. See <see cref="VideoFinderWindow"/> for the visible
/// "press play yourself" version.
/// </summary>
public sealed class WebViewVideoFinder : VideoFinderBase
{
    private static readonly TimeSpan LookTimeout = TimeSpan.FromSeconds(45);
    private static readonly TimeSpan PlayerWait = TimeSpan.FromSeconds(15);
    private static readonly TimeSpan SettleAfterFound = TimeSpan.FromSeconds(2.5);

    /// <summary>Mutes every video on the page and asks it to play (no clicks on anything else).</summary>
    public const string MuteAndPlay = "document.querySelectorAll('video').forEach(function(v){v.muted=true;try{var p=v.play();if(p&&p.catch)p.catch(function(){});}catch(e){}});";

    private readonly ILogger<WebViewVideoFinder> _logger;
    private readonly IServiceProvider _services;
    private readonly SemaphoreSlim _one;
    private readonly object _environmentLock = new object();
    private readonly Lazy<bool> _available;
    private Task<CoreWebView2Environment>? _environment;

    public WebViewVideoFinder(ILogger<WebViewVideoFinder> logger, IServiceProvider services)
    {
        _logger = logger;
        _services = services;
        _one = new SemaphoreSlim(1, 1);
        _available = new Lazy<bool>(() =>
        {
            try
            {
                return !string.IsNullOrEmpty(CoreWebView2Environment.GetAvailableBrowserVersionString());
            }
            catch
            {
                return false;
            }
        });
    }

    public override bool IsAvailable => _available.Value;

    /// <summary>The finder's private browser data (cookies etc.), separate from any other browser.</summary>
    public static string DataFolder => Path.Combine(System.Environment.GetFolderPath(System.Environment.SpecialFolder.LocalApplicationData), "UniFetcher", "VideoFinder");

    public static string CookieFile => Path.Combine(DataFolder, "cookies.txt");

    /// <summary>Marker: delete the finder's browser data before it next starts (it's locked while running).</summary>
    private static string ClearMarker => Path.Combine(Path.GetDirectoryName(DataFolder)!, "VideoFinder.clear");

    public override void Forget(bool alsoBrowserData)
    {
        base.Forget(alsoBrowserData);
        try
        {
            File.Delete(CookieFile);
            if (alsoBrowserData)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(ClearMarker)!);
                File.WriteAllText(ClearMarker, string.Empty);
            }
        }
        catch (Exception e)
        {
            _logger.LogWarning($"Couldn't clear the video finder's data: {e.Message}");
        }
    }

    /// <summary>One browser environment for the hidden finder and the finder window. Call on the UI thread.</summary>
    public Task<CoreWebView2Environment> GetEnvironmentAsync()
    {
        lock (_environmentLock)
        {
            if (_environment is null)
            {
                if (File.Exists(ClearMarker))
                {
                    // You cleared the browser data last time: start the finder's browser fresh
                    try
                    {
                        if (Directory.Exists(DataFolder))
                        {
                            Directory.Delete(DataFolder, true);
                        }
                        File.Delete(ClearMarker);
                    }
                    catch
                    {
                        // still in use; try again next time
                    }
                }
                Directory.CreateDirectory(DataFolder);
                var options = new CoreWebView2EnvironmentOptions { AdditionalBrowserArguments = "--autoplay-policy=no-user-gesture-required" };
                _environment = CoreWebView2Environment.CreateWithOptionsAsync(string.Empty, DataFolder, options).AsTask();
            }
            return _environment;
        }
    }

    /// <summary>Blocks pop-ups, new windows, page-started downloads, dialogs and non-web links (same rules as the Android app).</summary>
    public static void MakeSafe(CoreWebView2 core)
    {
        core.Settings.AreDefaultScriptDialogsEnabled = false;
        core.Settings.IsStatusBarEnabled = false;
        core.Settings.AreDevToolsEnabled = false;
        core.NewWindowRequested += (_, e) => e.Handled = true;
        core.DownloadStarting += (_, e) => e.Cancel = true;
        core.NavigationStarting += (_, e) =>
        {
            if (!e.Uri.StartsWith("http://", StringComparison.OrdinalIgnoreCase) && !e.Uri.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                e.Cancel = true;
            }
        };
    }

    /// <summary>Notes a response if it's a video stream; never changes what the page loads.</summary>
    public static StreamPicker.Kind? Observe(CoreWebView2WebResourceResponseReceivedEventArgs e, List<StreamPicker.Candidate> into)
    {
        string? type = null;
        try
        {
            if (e.Response.Headers.Contains("Content-Type"))
            {
                type = e.Response.Headers.GetHeader("Content-Type");
            }
        }
        catch
        {
            // headers aren't always readable
        }
        var kind = StreamPicker.Classify(e.Request.Uri, type);
        if (kind is not null)
        {
            lock (into)
            {
                into.Add(new StreamPicker.Candidate(e.Request.Uri, kind.Value));
            }
        }
        return kind;
    }

    /// <summary>Saves the finder browser's cookies for yt-dlp and returns the file, or null.</summary>
    public static async Task<string?> SaveCookiesAsync(CoreWebView2 core)
    {
        try
        {
            var list = await core.CookieManager.GetCookiesAsync(string.Empty);
            var cookies = list.Select(c => new NetscapeCookies.Cookie(c.Domain, c.Path, c.IsSecure, c.IsSession ? 0 : (long)c.Expires, c.Name, c.Value));
            Directory.CreateDirectory(DataFolder);
            await File.WriteAllTextAsync(CookieFile, NetscapeCookies.Format(cookies));
            return CookieFile;
        }
        catch
        {
            return null;
        }
    }

    public static string ToHttps(Uri page) => page.Scheme == Uri.UriSchemeHttp ? "https" + page.ToString()[4..] : page.ToString();

    protected override async Task<FoundVideo?> LookAsync(Uri page, CancellationToken cancellationToken)
    {
        if (!IsAvailable)
        {
            return null;
        }
        var window = _services.GetRequiredService<MainWindow>();
        await _one.WaitAsync(cancellationToken);
        try
        {
            var result = new TaskCompletionSource<FoundVideo?>();
            if (!window.DispatcherQueue.TryEnqueue(async () =>
            {
                try
                {
                    result.TrySetResult(await LookOnUiThreadAsync(window, page, cancellationToken));
                }
                catch (Exception e)
                {
                    _logger.LogWarning($"Video finder couldn't open {page}: {e.Message}");
                    result.TrySetResult(null);
                }
            }))
            {
                return null;
            }
            return await result.Task;
        }
        finally
        {
            _one.Release();
        }
    }

    private async Task<FoundVideo?> LookOnUiThreadAsync(Window window, Uri page, CancellationToken cancellationToken)
    {
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(window);
        var environment = await GetEnvironmentAsync();
        var controller = await environment.CreateCoreWebView2ControllerAsync(CoreWebView2ControllerWindowReference.CreateFromWindowHandle((ulong)hwnd));
        try
        {
            controller.IsVisible = false;
            var core = controller.CoreWebView2;
            core.IsMuted = true;
            MakeSafe(core);
            var seen = new List<StreamPicker.Candidate>();
            var done = new TaskCompletionSource();
            var timer = window.DispatcherQueue.CreateTimer();
            timer.IsRepeating = false;
            timer.Tick += (_, _) => done.TrySetResult();
            void FinishAfter(TimeSpan delay)
            {
                timer.Stop();
                timer.Interval = delay;
                timer.Start();
            }
            core.WebResourceResponseReceived += (_, e) =>
            {
                var kind = Observe(e, seen);
                if (kind is StreamPicker.Kind.Hls or StreamPicker.Kind.Dash)
                {
                    FinishAfter(SettleAfterFound);
                }
            };
            core.NavigationStarting += (_, _) => timer.Stop();
            core.NavigationCompleted += async (_, _) =>
            {
                try
                {
                    await core.ExecuteScriptAsync(MuteAndPlay);
                }
                catch
                {
                    // the page may already be gone
                }
                bool gotPlaylist;
                lock (seen)
                {
                    gotPlaylist = seen.Any(c => c.Kind != StreamPicker.Kind.File);
                }
                FinishAfter(gotPlaylist ? SettleAfterFound : PlayerWait);
            };
            using var cancel = cancellationToken.Register(() => done.TrySetResult());
            core.Navigate(ToHttps(page));
            await Task.WhenAny(done.Task, Task.Delay(LookTimeout, CancellationToken.None));
            timer.Stop();
            StreamPicker.Candidate? best;
            lock (seen)
            {
                best = StreamPicker.Pick(seen);
                _logger.LogInformation($"Video finder looked at {page.Host}: {seen.Count} video request(s), picked {(best is null ? "nothing" : StreamPicker.Describe(best))}");
            }
            if (best is null || cancellationToken.IsCancellationRequested)
            {
                return null;
            }
            var title = core.DocumentTitle;
            var cookies = await SaveCookiesAsync(core);
            return new FoundVideo(page, new Uri(best.Url), best.Kind, string.IsNullOrWhiteSpace(title) ? null : title, cookies, core.Settings.UserAgent);
        }
        finally
        {
            controller.Close();
        }
    }
}
