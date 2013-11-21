package developer;

public class Scratch {

        public static void main(String[] args) {

                System.out.println(65535 & 0x00ff);

        }

}

/*
private void addAlphaChannel(byte[] rgbBytes, int bytesLen, int[] argbInts)
{
    for(int i=0, j=0; i<bytesLen; i+=3, j++)
    {
        argbInts[j] = ((byte) 0xff) << 24 |                 // Alpha
                    (rgbBytes[i] << 16) & (0xff0000) |      // Red
                    (rgbBytes[i+1] << 8) & (0xff00) |       // Green
                    (rgbBytes[i+2]) & (0xff);               // Blue
    }
}
*/