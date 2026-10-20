import java.io.File;
import brut.androlib.src.SmaliDecoder;

public class DecodeC6 {
    public static void main(String[] args) throws Exception {
        File apk = new File("Truecaller_v26.38.50_LowyatThreatIntelligence_MOD_signed.apk");
        File outDir = new File("smali_c6");
        SmaliDecoder.decode(apk, outDir, "classes6.dex", false, 32);
        System.out.println("Decoded classes6.dex successfully");
    }
}
