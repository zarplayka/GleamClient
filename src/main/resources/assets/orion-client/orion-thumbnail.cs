using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Globalization;
using System.IO;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using Windows.Foundation;
using Windows.Media;
using Windows.Media.Control;
using Windows.Storage.Streams;

// A prebuilt Windows media-session bridge. Every command is scoped to a source application.
public static class OrionThumbnail
{
    private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

    public static int Main(string[] args)
    {
        Console.OutputEncoding = new UTF8Encoding(false);
        if (args.Length == 0) return 4;
        try
        {
            var manager = Await(GlobalSystemMediaTransportControlsSessionManager.RequestAsync());
            if (args[0] == "snapshot")
            {
                var selected = Select(manager, null);
                Console.WriteLine(Json.Serialize(selected == null ? new Dictionary<string, object>() : Snapshot(selected)));
                return 0;
            }
            if (args.Length < 2 || args[1].Length > 512) return 4;
            var session = Select(manager, args[1]);
            if (session == null) return 2;
            if (args[0] == "cover" && args.Length == 3) return Cover(session, args[2]);
            var playback = session.GetPlaybackInfo();
            var controls = playback.Controls;
            bool success = false;
            switch (args[0])
            {
                case "toggle":
                    if (controls.IsPlayPauseToggleEnabled) success = Await(session.TryTogglePlayPauseAsync());
                    else if (playback.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing && controls.IsPauseEnabled)
                        success = Await(session.TryPauseAsync());
                    else if (controls.IsPlayEnabled) success = Await(session.TryPlayAsync());
                    break;
                case "next":
                    if (controls.IsNextEnabled) success = Await(session.TrySkipNextAsync());
                    break;
                case "previous":
                    if (controls.IsPreviousEnabled) success = Await(session.TrySkipPreviousAsync());
                    break;
                case "shuffle":
                    if (controls.IsShuffleEnabled) success = Await(session.TryChangeShuffleActiveAsync(!(playback.IsShuffleActive ?? false)));
                    break;
                case "repeat":
                    var mode = playback.AutoRepeatMode;
                    var next = mode == MediaPlaybackAutoRepeatMode.List ? MediaPlaybackAutoRepeatMode.Track
                        : mode == MediaPlaybackAutoRepeatMode.Track ? MediaPlaybackAutoRepeatMode.None : MediaPlaybackAutoRepeatMode.List;
                    if (controls.IsRepeatEnabled) success = Await(session.TryChangeAutoRepeatModeAsync(next));
                    break;
                case "seek":
                    double seconds;
                    if (args.Length != 3 || !double.TryParse(args[2], NumberStyles.Float, CultureInfo.InvariantCulture, out seconds)
                        || double.IsNaN(seconds) || double.IsInfinity(seconds) || seconds < 0) return 4;
                    if (controls.IsPlaybackPositionEnabled)
                    {
                        var timeline = session.GetTimelineProperties();
                        double duration = Math.Max(0, (timeline.EndTime - timeline.StartTime).TotalSeconds);
                        long position = timeline.StartTime.Ticks + (long)(Math.Min(duration, seconds) * TimeSpan.TicksPerSecond);
                        success = Await(session.TryChangePlaybackPositionAsync(position));
                    }
                    break;
                default: return 4;
            }
            Console.WriteLine(success ? "{\"ok\":true}" : "{\"ok\":false}");
            return success ? 0 : 3;
        }
        catch { return 1; }
    }

    private static GlobalSystemMediaTransportControlsSession Select(GlobalSystemMediaTransportControlsSessionManager manager, string source)
    {
        var current = manager.GetCurrentSession();
        if (current != null && (source == null || current.SourceAppUserModelId == source)
            && current.GetPlaybackInfo().PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing) return current;
        GlobalSystemMediaTransportControlsSession fallback = null;
        foreach (var candidate in manager.GetSessions())
        {
            if (source != null && candidate.SourceAppUserModelId != source) continue;
            if (candidate.GetPlaybackInfo().PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing) return candidate;
            if (fallback == null) fallback = candidate;
        }
        return current != null && (source == null || current.SourceAppUserModelId == source) ? current : fallback;
    }

    private static Dictionary<string, object> Snapshot(GlobalSystemMediaTransportControlsSession session)
    {
        var properties = Await(session.TryGetMediaPropertiesAsync());
        var playback = session.GetPlaybackInfo();
        var controls = playback.Controls;
        var timeline = session.GetTimelineProperties();
        bool playing = playback.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
        double duration = Math.Max(0, (timeline.EndTime - timeline.StartTime).TotalSeconds);
        double position = Math.Max(0, (timeline.Position - timeline.StartTime).TotalSeconds);
        if (playing && timeline.LastUpdatedTime.Year > 2000) position += Math.Max(0, (DateTimeOffset.UtcNow - timeline.LastUpdatedTime).TotalSeconds);
        if (duration > 0) position = Math.Min(duration, position);
        return new Dictionary<string, object>
        {
            {"source", session.SourceAppUserModelId},
            {"title", Trim(string.IsNullOrWhiteSpace(properties.Title) ? properties.AlbumTitle : properties.Title, 512)},
            {"artist", Trim(string.IsNullOrWhiteSpace(properties.Artist) ? properties.AlbumArtist : properties.Artist, 256)},
            {"album", Trim(properties.AlbumTitle, 256)},
            {"playing", playing}, {"position", position}, {"duration", duration},
            {"shuffle", playback.IsShuffleActive ?? false},
            {"repeat", playback.AutoRepeatMode == MediaPlaybackAutoRepeatMode.Track ? 2 : playback.AutoRepeatMode == MediaPlaybackAutoRepeatMode.List ? 1 : 0},
            {"canPlay", controls.IsPlayPauseToggleEnabled || (playing ? controls.IsPauseEnabled : controls.IsPlayEnabled)},
            {"canNext", controls.IsNextEnabled}, {"canPrevious", controls.IsPreviousEnabled},
            {"canSeek", controls.IsPlaybackPositionEnabled && duration > 0},
            {"canShuffle", controls.IsShuffleEnabled}, {"canRepeat", controls.IsRepeatEnabled}
        };
    }

    private static int Cover(GlobalSystemMediaTransportControlsSession session, string path)
    {
        var properties = Await(session.TryGetMediaPropertiesAsync());
        if (properties.Thumbnail == null) return 3;
        using (var stream = Await(properties.Thumbnail.OpenReadAsync()))
        using (var reader = new DataReader(stream))
        using (var bytes = new MemoryStream())
        {
            uint count;
            while ((count = Await<uint>(reader.LoadAsync(64 * 1024))) > 0)
            {
                if (bytes.Length + count > 8 * 1024 * 1024) return 3;
                var chunk = new byte[count];
                reader.ReadBytes(chunk);
                bytes.Write(chunk, 0, chunk.Length);
            }
            bytes.Position = 0;
            using (var original = Image.FromStream(bytes))
            {
                int size = Math.Min(512, Math.Min(original.Width, original.Height));
                if (size <= 0) return 3;
                using (var square = new Bitmap(size, size))
                using (var graphics = Graphics.FromImage(square))
                {
                    int side = Math.Min(original.Width, original.Height);
                    graphics.DrawImage(original, new Rectangle(0, 0, size, size),
                        (original.Width - side) / 2, (original.Height - side) / 2, side, side, GraphicsUnit.Pixel);
                    square.Save(path, ImageFormat.Png);
                }
            }
        }
        return 0;
    }

    private static string Trim(string value, int length)
    {
        value = value ?? "";
        return value.Length <= length ? value : value.Substring(0, length);
    }

    private static T Await<T>(IAsyncOperation<T> operation)
    {
        try
        {
            while (operation.Status == AsyncStatus.Started) Thread.Sleep(5);
            return operation.GetResults();
        }
        finally { operation.Close(); }
    }
}
