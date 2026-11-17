using Nickvision.Desktop.Keyring;
using Nickvision.Desktop.System;
using Nickvision.Parabolic.Shared.Models;
using System;
using System.Collections.Generic;
using System.Diagnostics;

namespace Nickvision.Parabolic.Shared.Services;

public interface IYtdlpExecutableService : IDependencyExecutableService
{
    IReadOnlyList<string> GetDiscoveryProcessArguments(Uri url, Credential? credential);
    IReadOnlyList<string> GetDiscoveryProcessArguments(Uri url, Credential? credential, string? cookieProfile);
    /// <summary>UniFetcher: discovery for a stream found by the video finder (same referer, cookies and identity as its page).</summary>
    IReadOnlyList<string> GetDiscoveryProcessArguments(Uri url, Credential? credential, string? cookieProfile, FoundVideo? found);
    Process GetDownloadProcess(DownloadOptions downloadOptions);
}
