package net.simplr.woosimdp230l;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class ParseBitmap {

    private static final String TAG = "ParseBitmap";
    private String m_data;
    private Bitmap m_bmp;

    public ParseBitmap(Bitmap _bmp) {
        try {
            m_bmp = _bmp;
            Log.d(TAG, "Height:" + Integer.toString(m_bmp.getHeight()));
            Log.d(TAG, "Widht:" + Integer.toString(m_bmp.getWidth()));

        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public String ExtractGraphicsDataForCPCL(int _xpos, int _ypos) {
        m_data = "";
        int color = 0, bit = 0, currentValue = 0, redValue = 0, blueValue = 0, greenValue = 0;

        try {
            //Make sure the width is divisible by 8
            int loopWidth = 8 - (m_bmp.getWidth() % 8);
            if (loopWidth == 8)
                loopWidth = m_bmp.getWidth();
            else
                loopWidth += m_bmp.getWidth();

            m_data = "EG" + " " +
                    Integer.toString((loopWidth / 8)) + " " +
                    Integer.toString(m_bmp.getHeight()) + " " +
                    Integer.toString(_xpos) + " " +
                    Integer.toString(_ypos) + " ";

            for (int y = 0; y < m_bmp.getHeight(); y++) {
                bit = 128;
                currentValue = 0;
                for (int x = 0; x < loopWidth; x++) {
                    int intensity = 0;

                    if (x < m_bmp.getWidth()) {
                        color = m_bmp.getPixel(x, y);

                        redValue = Color.red(color);
                        blueValue = Color.blue(color);
                        greenValue = Color.green(color);

                        intensity = 255 - ((redValue + greenValue + blueValue) / 3);
                    } else
                        intensity = 0;


                    if (intensity >= 128)
                        currentValue |= bit;
                    bit = bit >> 1;
                    if (bit == 0) {
                        String hex = Integer.toHexString(currentValue);
                        hex = LeftPad(hex);
                        m_data = m_data + hex.toUpperCase();
                        bit = 128;
                        currentValue = 0;

                        /****
                         String dbg = "x,y" + "-"+ Integer.toString(x) + "," + Integer.toString(y) + "-" +
                         "Col:" + Integer.toString(color) + "-" +
                         "Red: " +  Integer.toString(redValue) + "-" +
                         "Blue: " +  Integer.toString(blueValue) + "-" +
                         "Green: " +  Integer.toString(greenValue) + "-" +
                         "Hex: " + hex;

                         Log.d(TAG,dbg);
                         *****/

                    }
                }//x
            }//y
            m_data = m_data + "\r\n";

        } catch (Exception e) {
            m_data = e.getMessage();
            return m_data;
        }

        return m_data;
    }

    private String LeftPad(String _num) {

        String str = _num;

        if (_num.length() == 1) {
            str = "0" + _num;
        }

        return str;
    }
}