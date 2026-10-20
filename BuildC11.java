import java.io.File;
import brut.androlib.src.SmaliBuilder;
import brut.directory.ExtFile;

public class BuildC11 {
    public static void main(String[] args) throws Exception {
        ExtFile smaliDir = new ExtFile("smali_c11");
        File dexOut = new File("test_classes11_rebuilt.dex");
        SmaliBuilder.build(smaliDir, dexOut, 32);
        System.out.println("Rebuilt test_classes11_rebuilt.dex successfully! Size: " + dexOut.length());
    }
}
