using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nickvision.Desktop.Globalization;
using Nickvision.Parabolic.Shared.Helpers;
using Nickvision.Parabolic.Shared.Services;
using Nickvision.Parabolic.WinUI.Helpers;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace Nickvision.Parabolic.WinUI.Views;

/// <summary>
/// UniFetcher video finder, "Action needed" version: shows the page in the finder's own browser so you can pass a check,
/// log in or press play yourself. When the page plays a video, "Use this video" downloads it. Nothing here is automated.
/// Built in code (no XAML) to keep it self-contained.
/// </summary>
public sealed class VideoFinderWindow : Window
{
    private readonly WebViewVideoFinder _finder;
    private readonly ITranslationService _t;
    private readonly Uri _page;
    private readonly Func<Uri, Task> _onUse;
    private readonly List<StreamPicker.Candidate> _seen;
    private readonly WebView2 _web;
    private readonly TextBlock _status;
    private readonly Button _use;

    public VideoFinderWindow(WebViewVideoFinder finder, ITranslationService translationService, Uri page, Func<Uri, Task> onUse)
    {
        _finder = finder;
        _t = translationService;
        _page = page;
        _onUse = onUse;
        _seen = new List<StreamPicker.Candidate>();
        Title = _t._("UniFetcher – Find the video");
        SystemBackdrop = new MicaBackdrop();
        AppWindow.Resize(new Windows.Graphics.SizeInt32(1100, 800));
        AppWindow.SetIcon("./Assets/unifetcher.ico");

        var root = new Grid { RowSpacing = 8, Padding = new Thickness(12) };
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        var bar = new Grid { ColumnSpacing = 8 };
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        _status = new TextBlock
        {
            Text = _t._("Pass any check or log in if the page asks, then press play on the video."),
            TextWrapping = TextWrapping.Wrap,
            VerticalAlignment = VerticalAlignment.Center
        };
        _use = new Button { Content = _t._("Use this video"), IsEnabled = false, Style = Application.Current.Resources["AccentButtonStyle"] as Style };
        _use.Click += async (_, _) => await UseAsync();
        var close = new Button { Content = _t._("Close") };
        close.Click += (_, _) => Close();
        Grid.SetColumn(_use, 1);
        Grid.SetColumn(close, 2);
        bar.Children.Add(_status);
        bar.Children.Add(_use);
        bar.Children.Add(close);
        root.Children.Add(bar);

        _web = new WebView2();
        Grid.SetRow(_web, 1);
        root.Children.Add(_web);
        Content = root;
        _web.Loaded += async (_, _) => await StartAsync();
    }

    private async Task StartAsync()
    {
        try
        {
            await _web.EnsureCoreWebView2Async(await _finder.GetEnvironmentAsync());
            var core = _web.CoreWebView2;
            WebViewVideoFinder.MakeSafe(core);
            core.WebResourceResponseReceived += (_, e) =>
            {
                if (WebViewVideoFinder.Observe(e, _seen) is not null && !_use.IsEnabled)
                {
                    _use.IsEnabled = true;
                    _status.Text = _t._("Video found ✓ Click \"Use this video\" to download it.");
                }
            };
            core.Navigate(WebViewVideoFinder.ToHttps(_page));
        }
        catch (Exception e)
        {
            _status.Text = _t._("The built-in browser couldn't start: {0}", e.Message);
        }
    }

    private async Task UseAsync()
    {
        StreamPicker.Candidate? best;
        lock (_seen)
        {
            best = StreamPicker.Pick(_seen);
        }
        if (best is null || _web.CoreWebView2 is null)
        {
            return;
        }
        _use.IsEnabled = false;
        var core = _web.CoreWebView2;
        var title = core.DocumentTitle;
        var cookies = await WebViewVideoFinder.SaveCookiesAsync(core);
        _finder.Remember(new FoundVideo(_page, new Uri(best.Url), best.Kind, string.IsNullOrWhiteSpace(title) ? null : title, cookies, core.Settings.UserAgent));
        Close();
        await _onUse(_page);
    }
}
