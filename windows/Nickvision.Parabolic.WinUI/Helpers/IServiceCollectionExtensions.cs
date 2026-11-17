using Microsoft.Extensions.DependencyInjection;
using Nickvision.Parabolic.WinUI.Controls;
using Nickvision.Parabolic.WinUI.Views;

namespace Nickvision.Parabolic.WinUI.Helpers;

public static class IServiceCollectionExtensions
{
    extension(IServiceCollection collection)
    {
        public IServiceCollection AddControls()
        {
            collection.AddTransient<AddDownloadDialog>();
            collection.AddTransient<HistoryDialog>();
            collection.AddTransient<KeyringDialog>();
            collection.AddSingleton<MainWindow>();
            collection.AddTransient<SettingsPage>();
            collection.AddSingleton<AboutDialog>();
            collection.AddTransient<DownloadRow>();
            // UniFetcher video finder (WebView2); replaces the "no browser" finder registered by the shared code
            collection.AddSingleton<WebViewVideoFinder>();
            collection.AddSingleton<Nickvision.Parabolic.Shared.Services.IVideoFinder>(sp => sp.GetRequiredService<WebViewVideoFinder>());
            return collection;
        }
    }
}
