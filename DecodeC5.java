import java.io.File;
import brut.androlib.src.SmaliDecoder;

public class DecodeC5 {
    public static void main(String[] args) throws Exception {
        File apk = new File("_base_backup/truecaller_original_base.apk");
        File outDir = new File("temp_c5");
        SmaliDecoder.decode(apk, outDir, "classes5.dex", false, 32);
        System.out.println("Decoded classes5.dex successfully");
    }
}