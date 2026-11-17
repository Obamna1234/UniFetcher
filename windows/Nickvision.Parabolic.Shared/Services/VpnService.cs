using Microsoft.Extensions.Logging;
using Nickvision.Desktop.Application;
using Nickvision.Parabolic.Shared.Helpers;
using System;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.NetworkInformation;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace Nickvision.Parabolic.Shared.Services;

/// <summary>What "Test VPN" saw: the IP address and place websites see.</summary>
public record VpnTestResult(
    [property: JsonPropertyName("ip")] string? Ip,
    [property: JsonPropertyName("country")] string? Country,
    [property: JsonPropertyName("city")] string? City,
    [property: JsonPropertyName("organization")] string? Organization);

/// <summary>
/// VPN status and test (Android spec 4h, same on Windows).
/// - <see cref="IsVpnOn"/> comes from Windows' own network adapters: nothing is contacted to work it out.
/// - "Pause downloads when no VPN is active" (off by default) pauses the queue while it's off (see QueueGuardService/DownloadService).
/// - <see cref="TestAsync"/> asks one "what is my IP" service, only when you press "Test VPN".
/// </summary>
public interface IVpnService
{
    /// <summary>The only service "Test VPN" contacts, and only when the button is pressed.</summary>
    const string TestUrl = "https://am.i.mullvad.net/json";

    bool IsVpnOn { get; }

    /// <summary>Raised (on a background thread) when the VPN turns on or off.</summary>
    event EventHandler<bool>? Changed;

    /// <summary>False while "Pause downloads when no VPN is active" is on and the VPN is off.</summary>
    bool CanDownload { get; }

    Task<VpnTestResult> TestAsync(CancellationToken cancellationToken = default);
}

public class VpnService : IVpnService, IDisposable
{
    private readonly ILogger<VpnService> _logger;
    private readonly IConfigurationService _configurationService;
    private readonly Timer _poll;
    private bool _isVpnOn;

    public event EventHandler<bool>? Changed;

    public VpnService(ILogger<VpnService> logger, IConfigurationService configurationService)
    {
        _logger = logger;
        _configurationService = configurationService;
        _isVpnOn = Detect();
        NetworkChange.NetworkAddressChanged += OnNetworkChanged;
        NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;
        // Some VPN apps switch adapters without an address change: check every few seconds as well
        _poll = new Timer(_ => Refresh(), null, TimeSpan.FromSeconds(5), TimeSpan.FromSeconds(5));
    }

    public bool IsVpnOn => _isVpnOn;

    public bool CanDownload => !_configurationService.PauseWithoutVpn || _isVpnOn;

    public void Dispose()
    {
        NetworkChange.NetworkAddressChanged -= OnNetworkChanged;
        NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
        _poll.Dispose();
        GC.SuppressFinalize(this);
    }

    private void OnNetworkChanged(object? sender, EventArgs e) => Refresh();

    private void OnNetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e) => Refresh();

    private void Refresh()
    {
        var now = Detect();
        if (now == _isVpnOn)
        {
            return;
        }
        _isVpnOn = now;
        _logger.LogInformation($"VPN is now {(now ? "on" : "off")}");
        Changed?.Invoke(this, now);
    }

    private static bool Detect()
    {
        try
        {
            return NetworkInterface.GetAllNetworkInterfaces()
                .Where(n => n.OperationalStatus == OperationalStatus.Up)
                .Any(n => IsVpnAdapter(n.Name, n.Description, n.NetworkInterfaceType));
        }
        catch
        {
            return false;
        }
    }

    /// <summary>True for the network adapters VPN apps add (PIA, WireGuard, OpenVPN, Windows' own VPN...).</summary>
    public static bool IsVpnAdapter(string name, string description, NetworkInterfaceType type)
    {
        if (type is NetworkInterfaceType.Loopback)
        {
            return false;
        }
        var text = (name + " " + description).ToLowerInvariant();
        // Not VPNs, even though some are tunnels
        string[] notVpn = ["teredo", "isatap", "6to4", "loopback", "hyper-v", "vethernet", "virtualbox", "vmware", "bluetooth", "wsl", "docker"];
        if (notVpn.Any(text.Contains))
        {
            return false;
        }
        string[] vpnWords = ["vpn", "wireguard", "wintun", "tap-windows", "tap-pia", "openvpn", "private internet access", "pia ", "wgpia", "nordlynx", "mullvad", "proton", "expressvpn", "surfshark", "windscribe", "tunnel", "fortinet", "cisco anyconnect", "globalprotect", "zerotier", "tailscale"];
        return type is NetworkInterfaceType.Ppp || vpnWords.Any(text.Contains);
    }

    public async Task<VpnTestResult> TestAsync(CancellationToken cancellationToken = default)
    {
        using var handler = new HttpClientHandler();
        // Same connection downloads use: UniFetcher's proxy setting if there is one
        if (!string.IsNullOrEmpty(_configurationService.ProxyUrl) && Uri.TryCreate(_configurationService.ProxyUrl, UriKind.Absolute, out var proxy))
        {
            handler.Proxy = new WebProxy(proxy);
            handler.UseProxy = true;
        }
        using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(15) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("UniFetcher");
        var json = await client.GetStringAsync(IVpnService.TestUrl, cancellationToken);
        return JsonSerializer.Deserialize<VpnTestResult>(json) ?? new VpnTestResult(null, null, null, null);
    }
}
