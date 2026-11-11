using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Text;

// Controls only Core Audio sessions belonging to the selected media application.
namespace OrionVolumeHelper
{
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    internal class DeviceEnumerator { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IDevices
    {
        [PreserveSig] int EnumAudioEndpoints(int flow, int states, out IDeviceCollection devices);
    }

    [ComImport, Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IDeviceCollection
    {
        [PreserveSig] int GetCount(out uint count);
        [PreserveSig] int Item(uint index, out IDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IDevice
    {
        [PreserveSig] int Activate(ref Guid iid, int context, IntPtr parameters,
            [MarshalAs(UnmanagedType.IUnknown)] out object result);
    }

    [ComImport, Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IManager
    {
        [PreserveSig] int GetAudioSessionControl(IntPtr id, uint flags, out IntPtr control);
        [PreserveSig] int GetSimpleAudioVolume(IntPtr id, uint flags, out IntPtr volume);
        [PreserveSig] int GetSessionEnumerator(out ISessions sessions);
    }

    [ComImport, Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ISessions
    {
        [PreserveSig] int GetCount(out int count);
        [PreserveSig] int GetSession(int index, out IBaseSession session);
    }

    [ComImport, Guid("F4B1A599-7266-4319-A8CA-E70ACB11E8CD"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IBaseSession { }

    [ComImport, Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ISession
    {
        [PreserveSig] int GetState(out int state);
        [PreserveSig] int GetDisplayName([MarshalAs(UnmanagedType.LPWStr)] out string name);
        [PreserveSig] int SetDisplayName([MarshalAs(UnmanagedType.LPWStr)] string name, ref Guid context);
        [PreserveSig] int GetIconPath([MarshalAs(UnmanagedType.LPWStr)] out string path);
        [PreserveSig] int SetIconPath([MarshalAs(UnmanagedType.LPWStr)] string path, ref Guid context);
        [PreserveSig] int GetGroupingParam(out Guid grouping);
        [PreserveSig] int SetGroupingParam(ref Guid grouping, ref Guid context);
        [PreserveSig] int RegisterAudioSessionNotification(IntPtr client);
        [PreserveSig] int UnregisterAudioSessionNotification(IntPtr client);
        [PreserveSig] int GetSessionIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetSessionInstanceIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetProcessId(out uint processId);
        [PreserveSig] int IsSystemSoundsSession();
    }

    [ComImport, Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IVolume
    {
        [PreserveSig] int SetMasterVolume(float value, ref Guid context);
        [PreserveSig] int GetMasterVolume(out float value);
        [PreserveSig] int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, ref Guid context);
        [PreserveSig] int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
    }

    internal static class Program
    {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
        private static extern int GetApplicationUserModelId(IntPtr process, ref uint length, StringBuilder name);
        [DllImport("kernel32.dll")]
        private static extern IntPtr OpenProcess(uint access, bool inherit, uint id);
        [DllImport("kernel32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool CloseHandle(IntPtr handle);

        private static void Release(object instance)
        {
            if (instance != null && Marshal.IsComObject(instance)) Marshal.ReleaseComObject(instance);
        }

        private static bool Matches(uint pid, string source)
        {
            if (pid == 0) return false;
            try
            {
                using (var process = Process.GetProcessById((int)pid))
                {
                    string name = process.ProcessName;
                    if (source.Equals(name, StringComparison.OrdinalIgnoreCase)
                        || source.Equals(name + ".exe", StringComparison.OrdinalIgnoreCase)) return true;
                    IntPtr handle = OpenProcess(0x1000, false, pid);
                    if (handle == IntPtr.Zero) return false;
                    try
                    {
                        uint length = 0;
                        if (GetApplicationUserModelId(handle, ref length, null) != 122 || length > 1024) return false;
                        var value = new StringBuilder((int)length);
                        return GetApplicationUserModelId(handle, ref length, value) == 0
                            && source.Equals(value.ToString(), StringComparison.Ordinal);
                    }
                    finally { CloseHandle(handle); }
                }
            }
            catch (ArgumentException) { return false; }
            catch (System.ComponentModel.Win32Exception) { return false; }
            catch (InvalidOperationException) { return false; }
        }

        [MTAThread]
        private static int Main(string[] args)
        {
            if (args.Length == 2 && args[0] == "--beat") return GleamBeat.Run(args[1]);
            if (args.Length < 1 || args.Length > 2 || string.IsNullOrWhiteSpace(args[0]) || args[0].Length > 512) return 2;
            float requested = -1;
            if (args.Length == 2 && (!float.TryParse(args[1], NumberStyles.Float, CultureInfo.InvariantCulture, out requested)
                || float.IsNaN(requested) || float.IsInfinity(requested) || requested < 0 || requested > 1)) return 2;
            object enumerator = null;
            IDeviceCollection devices = null;
            int matched = 0;
            float total = 0;
            try
            {
                enumerator = new DeviceEnumerator();
                Marshal.ThrowExceptionForHR(((IDevices)enumerator).EnumAudioEndpoints(0, 1, out devices));
                uint count;
                Marshal.ThrowExceptionForHR(devices.GetCount(out count));
                var identities = new Dictionary<uint, bool>();
                for (uint i = 0; i < count; i++)
                {
                    IDevice device = null;
                    object managerObject = null;
                    ISessions sessions = null;
                    try
                    {
                        if (devices.Item(i, out device) < 0) continue;
                        Guid iid = typeof(IManager).GUID;
                        if (device.Activate(ref iid, 23, IntPtr.Zero, out managerObject) < 0) continue;
                        if (((IManager)managerObject).GetSessionEnumerator(out sessions) < 0) continue;
                        int sessionCount;
                        if (sessions.GetCount(out sessionCount) < 0) continue;
                        for (int j = 0; j < sessionCount; j++)
                        {
                            IBaseSession raw = null;
                            try
                            {
                                if (sessions.GetSession(j, out raw) < 0) continue;
                                var session = raw as ISession;
                                if (session == null || session.IsSystemSoundsSession() == 0) continue;
                                int state;
                                uint pid;
                                if (session.GetState(out state) < 0 || state == 2 || session.GetProcessId(out pid) != 0) continue;
                                bool match;
                                if (!identities.TryGetValue(pid, out match))
                                {
                                    match = Matches(pid, args[0]);
                                    identities[pid] = match;
                                }
                                if (!match) continue;
                                var volume = raw as IVolume;
                                if (volume == null) continue;
                                Guid context = Guid.Empty;
                                if (requested >= 0)
                                {
                                    Marshal.ThrowExceptionForHR(volume.SetMasterVolume(requested, ref context));
                                    if (requested > 0) Marshal.ThrowExceptionForHR(volume.SetMute(false, ref context));
                                }
                                float value;
                                bool mute;
                                if (volume.GetMasterVolume(out value) < 0 || volume.GetMute(out mute) < 0) continue;
                                total += mute ? 0 : value;
                                matched++;
                            }
                            finally { Release(raw); }
                        }
                    }
                    finally
                    {
                        Release(sessions);
                        Release(managerObject);
                        Release(device);
                    }
                }
                if (matched == 0) return 3;
                Console.WriteLine((total / matched).ToString("0.####", CultureInfo.InvariantCulture));
                return 0;
            }
            catch (Exception) { return 1; }
            finally { Release(devices); Release(enumerator); }
        }
    }
}
