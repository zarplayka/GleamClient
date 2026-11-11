using System;
using OrionVolumeHelper;

internal static class GleamBeatAnalysisTest
{
    private static void Check(bool value, string message) { if(!value)throw new Exception(message); }
    private static GleamBeat.Analyzer Signal(double frequency, double amplitude)
    {
        var result=new GleamBeat.Analyzer(48000);
        for(int frame=0;frame<30;frame++)
        {
            for(int i=0;i<1920;i++)result.Add(amplitude*Math.Sin(2*Math.PI*frequency*(frame*1920+i)/48000));
            result.Finish(frame*40+1000);
        }
        return result;
    }
    public static void Main()
    {
        var silence=Signal(100,0);Check(silence.Level==0&&silence.Bass==0&&silence.Onsets==0,"Silence must not generate a beat");
        var bass=Signal(100,.25);var treble=Signal(4000,.25);
        Check(bass.Bass>.5&&bass.Level>.5,"Bass energy detected");
        Check(treble.Bass<bass.Bass*.15,"Low-pass rejects high frequencies");
        Check(bass.Onsets>0&&bass.Onsets<5,"Adaptive onset threshold/refractory period");
        bass.Add(double.NaN);bass.Add(double.PositiveInfinity);bass.Finish(3000);
        Check(!double.IsNaN(bass.Level+bass.Bass),"Non-finite samples sanitized");
        Console.WriteLine("Beat analysis checks passed: 5");
    }
}
