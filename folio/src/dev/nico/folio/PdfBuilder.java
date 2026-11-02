package dev.nico.folio;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Escribe un PDF con una imagen JPEG por página. Las imágenes van tal cual
 * (filtro DCTDecode), así que el archivo pesa casi lo mismo que las fotos.
 */
final class PdfBuilder {
    static final int SIZE_A4 = 0;
    static final int SIZE_LETTER = 1;
    static final int SIZE_FIT = 2;

    private static final Charset ASCII = Charset.forName("US-ASCII");

    private final Counter out;
    private final ArrayList<Long> offsets = new ArrayList<>();
    private final ArrayList<Integer> pages = new ArrayList<>();

    PdfBuilder(OutputStream os) throws IOException {
        out = new Counter(new BufferedOutputStream(os, 1 << 16));
        ascii("%PDF-1.4\n");
        out.write(new byte[]{'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});
        offsets.add(0L); // 1: catálogo
        offsets.add(0L); // 2: árbol de páginas
        offsets.add(0L); // 3: información
    }

    long bytesWritten() {
        return out.count;
    }

    int pageCount() {
        return pages.size();
    }

    /**
     * Calcula la página para una imagen de {@code iw}×{@code ih} píxeles.
     * Devuelve {anchoPágina, altoPágina, x, y, anchoImagen, altoImagen} en puntos.
     */
    static float[] layout(int size, boolean margin, int iw, int ih) {
        float m = margin ? 28.35f : 0f; // 1 cm
        if (size == SIZE_FIT) {
            float pw = 595.28f;
            float w = pw - 2 * m;
            float h = w * ih / (float) iw;
            if (h + 2 * m > 14000f) {
                float k = (14000f - 2 * m) / h;
                h *= k;
                w *= k;
            }
            return new float[]{w + 2 * m, h + 2 * m, m, m, w, h};
        }
        float pw = size == SIZE_LETTER ? 612f : 595.28f;
        float ph = size == SIZE_LETTER ? 792f : 841.89f;
        if (iw > ih) {
            float t = pw;
            pw = ph;
            ph = t;
        }
        float s = Math.min((pw - 2 * m) / iw, (ph - 2 * m) / ih);
        float w = iw * s, h = ih * s;
        return new float[]{pw, ph, (pw - w) / 2f, (ph - h) / 2f, w, h};
    }

    void addJpegPage(byte[] jpeg, int iw, int ih, float[] box) throws IOException {
        int img = reserve(), content = reserve(), page = reserve();

        begin(img);
        ascii("<< /Type /XObject /Subtype /Image /Width " + iw + " /Height " + ih
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpeg.length + " >>\nstream\n");
        out.write(jpeg);
        ascii("\nendstream\nendobj\n");

        byte[] cs = ("q\n" + f(box[4]) + " 0 0 " + f(box[5]) + " " + f(box[2]) + " " + f(box[3])
                + " cm\n/Im0 Do\nQ\n").getBytes(ASCII);
        begin(content);
        ascii("<< /Length " + cs.length + " >>\nstream\n");
        out.write(cs);
        ascii("\nendstream\nendobj\n");

        begin(page);
        ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + f(box[0]) + " " + f(box[1])
                + "] /Resources << /XObject << /Im0 " + img + " 0 R >> /ProcSet [/PDF /ImageC] >> /Contents "
                + content + " 0 R >>\nendobj\n");
        pages.add(page);
    }

    void finish(String title) throws IOException {
        begin(1);
        ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        StringBuilder kids = new StringBuilder();
        for (int p : pages) kids.append(p).append(" 0 R ");
        begin(2);
        ascii("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pages.size() + " >>\nendobj\n");

        String date = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
        begin(3);
        ascii("<< /Producer (Folio) /Creator (Folio) /Title " + utf16(title)
                + " /CreationDate (D:" + date + ") >>\nendobj\n");

        long xref = out.count;
        int size = offsets.size() + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("xref\n0 ").append(size).append('\n');
        sb.append("0000000000 65535 f \n");
        for (long off : offsets) sb.append(String.format(Locale.US, "%010d 00000 n \n", off));
        sb.append("trailer\n<< /Size ").append(size).append(" /Root 1 0 R /Info 3 0 R >>\n");
        sb.append("startxref\n").append(xref).append("\n%%EOF\n");
        ascii(sb.toString());
        out.flush();
    }

    private int reserve() {
        offsets.add(0L);
        return offsets.size();
    }

    private void begin(int obj) throws IOException {
        offsets.set(obj - 1, out.count);
        ascii(obj + " 0 obj\n");
    }

    private void ascii(String s) throws IOException {
        out.write(s.getBytes(ASCII));
    }

    private static String f(float v) {
        String s = String.format(Locale.US, "%.3f", v);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    /** Cadena de texto PDF en UTF-16BE, para que salgan bien las tildes y la ñ. */
    private static String utf16(String s) {
        StringBuilder sb = new StringBuilder("<FEFF");
        for (int i = 0; i < s.length(); i++) sb.append(String.format(Locale.US, "%04X", (int) s.charAt(i)));
        return sb.append('>').toString();
    }

    private static final class Counter extends FilterOutputStream {
        long count;

        Counter(OutputStream os) {
            super(os);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
