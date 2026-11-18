using System;
using System.Diagnostics;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Threading;

namespace OrionVolumeHelper
{
    // Только анализ системного выхода WASAPI: микрофон, файлы и сеть не используются.
    internal static class GleamBeat
    {
        [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface Endpoints
        {
            [PreserveSig] int EnumAudioEndpoints(int flow, int states, out IntPtr devices);
            [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IDevice device);
        }
        [ComImport, Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface AudioClient
        {
            [PreserveSig] int Initialize(int mode, uint flags, long duration, long period, IntPtr format, IntPtr session);
            [PreserveSig] int GetBufferSize(out uint frames);
            [PreserveSig] int GetStreamLatency(out long latency);
            [PreserveSig] int GetCurrentPadding(out uint padding);
            [PreserveSig] int IsFormatSupported(int mode, IntPtr format, out IntPtr closest);
            [PreserveSig] int GetMixFormat(out IntPtr format);
            [PreserveSig] int GetDevicePeriod(out long normal, out long minimum);
            [PreserveSig] int Start();
            [PreserveSig] int Stop();
            [PreserveSig] int Reset();
            [PreserveSig] int SetEventHandle(IntPtr handle);
            [PreserveSig] int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
        }
        [ComImport, Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface CaptureClient
        {
            [PreserveSig] int GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong position, out ulong time);
            [PreserveSig] int ReleaseBuffer(uint frames);
            [PreserveSig] int GetNextPacketSize(out uint frames);
        }

        internal sealed class Analyzer
        {
            private readonly double coefficient;
            private double low, energy, bassEnergy, average;
            private int samples;
            private long lastOnset;
            internal long Onsets;
            internal double Level, Bass;
            internal Analyzer(int sampleRate) { coefficient = 1 - Math.Exp(-2 * Math.PI * 160 / sampleRate); }
            internal void Add(double sample)
            {
                if (double.IsNaN(sample) || double.IsInfinity(sample)) sample = 0;
                sample = Math.Max(-1, Math.Min(1, sample));
                low += coefficient * (sample - low);
                energy += sample * sample; bassEnergy += low * low; samples++;
            }
            internal void Finish(long milliseconds)
            {
                double rms = samples == 0 ? 0 : Math.Sqrt(energy / samples);
                double bass = samples == 0 ? 0 : Math.Sqrt(bassEnergy / samples);
                // Адаптивный порог и refractory-период; никакого выдуманного BPM.
                if (bass > Math.Max(.012, average * 1.55) && milliseconds - lastOnset > 180)
                { Onsets++; lastOnset = milliseconds; }
                average += .075 * (bass - average);
                Level = Math.Min(1, rms * 4); Bass = Math.Min(1, bass * 5);
                energy = bassEnergy = 0; samples = 0;
            }
        }

        private static void Release(object value)
        { if (value != null && Marshal.IsComObject(value)) Marshal.ReleaseComObject(value); }

        internal static int Run(string parentId)
        {
            int pid;
            if (!int.TryParse(parentId, out pid) || pid < 1) return 2;
            object enumerator = null, rawClient = null, rawCapture = null;
            IDevice device = null;
            IntPtr format = IntPtr.Zero;
            AudioClient client = null;
            try
            {
                using (var parent = Process.GetProcessById(pid))
                {
                    enumerator = new DeviceEnumerator();
                    Marshal.ThrowExceptionForHR(((Endpoints)enumerator).GetDefaultAudioEndpoint(0, 0, out device));
                    Guid iid = typeof(AudioClient).GUID;
                    Marshal.ThrowExceptionForHR(device.Activate(ref iid, 23, IntPtr.Zero, out rawClient));
                    client = (AudioClient)rawClient;
                    Marshal.ThrowExceptionForHR(client.GetMixFormat(out format));
                    int tag = (ushort)Marshal.ReadInt16(format), channels = (ushort)Marshal.ReadInt16(format, 2);
                    int rate = Marshal.ReadInt32(format, 4), stride = (ushort)Marshal.ReadInt16(format, 12);
                    int bits = (ushort)Marshal.ReadInt16(format, 14);
                    if (tag == 0xFFFE && (ushort)Marshal.ReadInt16(format, 16) >= 22) tag = Marshal.ReadInt32(format, 24);
                    if (channels < 1 || channels > 32 || rate < 8000 || rate > 384000 || stride < channels * (bits / 8)
                        || !((tag == 3 && bits == 32) || (tag == 1 && (bits == 16 || bits == 24 || bits == 32)))) return 3;
                    Marshal.ThrowExceptionForHR(client.Initialize(0, 0x20000, 1000000, 0, format, IntPtr.Zero));
                    uint bufferFrames;
                    Marshal.ThrowExceptionForHR(client.GetBufferSize(out bufferFrames));
                    if ((long)bufferFrames * stride > 4000000) return 3;
                    byte[] buffer = new byte[checked((int)bufferFrames * stride)];
                    iid = typeof(CaptureClient).GUID;
                    Marshal.ThrowExceptionForHR(client.GetService(ref iid, out rawCapture));
                    var capture = (CaptureClient)rawCapture;
                    var analyzer = new Analyzer(rate);
                    var timer = Stopwatch.StartNew();
                    long sent = -40;
                    Marshal.ThrowExceptionForHR(client.Start());
                    while (!parent.HasExited)
                    {
                        uint packet;
                        Marshal.ThrowExceptionForHR(capture.GetNextPacketSize(out packet));
                        while (packet > 0)
                        {
                            IntPtr data; uint frames, flags; ulong position, timestamp;
                            Marshal.ThrowExceptionForHR(capture.GetBuffer(out data, out frames, out flags, out position, out timestamp));
                            try
                            {
                                int length = checked((int)frames * stride);
                                if (length > buffer.Length) return 3;
                                bool silent = (flags & 2) != 0;
                                if (!silent) Marshal.Copy(data, buffer, 0, length);
                                for (int frame = 0; frame < frames; frame++)
                                {
                                    double sample = 0;
                                    if (!silent) for (int channel = 0; channel < channels; channel++)
                                    {
                                        int at = frame * stride + channel * (bits / 8);
                                        if (tag == 3) sample += BitConverter.ToSingle(buffer, at);
                                        else if (bits == 16) sample += BitConverter.ToInt16(buffer, at) / 32768.0;
                                        else if (bits == 32) sample += BitConverter.ToInt32(buffer, at) / 2147483648.0;
                                        else sample += ((buffer[at] | buffer[at + 1] << 8 | buffer[at + 2] << 16) << 8 >> 8) / 8388608.0;
                                    }
                                    analyzer.Add(sample / channels);
                                }
                            }
                            finally { capture.ReleaseBuffer(frames); }
                            Marshal.ThrowExceptionForHR(capture.GetNextPacketSize(out packet));
                        }
                        if (timer.ElapsedMilliseconds - sent >= 40)
                        {
                            sent = timer.ElapsedMilliseconds; analyzer.Finish(sent);
                            Console.WriteLine(analyzer.Level.ToString("0.000", CultureInfo.InvariantCulture) + " "
                                + analyzer.Bass.ToString("0.000", CultureInfo.InvariantCulture) + " " + analyzer.Onsets);
                            Console.Out.Flush();
                        }
                        Thread.Sleep(10);
                    }
                }
                return 0;
            }
            catch (Exception) { return 1; }
            finally
            {
                if (client != null) client.Stop();
                if (format != IntPtr.Zero) Marshal.FreeCoTaskMem(format);
                Release(rawCapture); Release(rawClient); Release(device); Release(enumerator);
            }
        }
    }
}
