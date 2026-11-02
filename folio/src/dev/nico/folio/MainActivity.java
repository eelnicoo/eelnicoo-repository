package dev.nico.folio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements PageGrid.Listener, Sheet.Host {
    private static final int REQ_PHOTOS = 1;
    private static final int REQ_FILES = 2;
    private static final int THUMB_SIDE = 480;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newFixedThreadPool(2);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private FrameLayout root;
    private LinearLayout header;
    private TextView subtitle;
    private ImageView moreButton;
    private View divider;
    private ScrollView scroll;
    private PageGrid grid;
    private LinearLayout empty;
    private LinearLayout bottomBar;
    private LinearLayout addButton;
    private LinearLayout createButton;
    private TextView snack;
    private Sheet sheet;
    private Exporter exporter;

    private int insetTop, insetBottom, insetLeft, insetRight, insetIme;
    private int failedLoads;
    private final Runnable hideSnack = new Runnable() {
        @Override
        public void run() {
            hideSnack();
        }
    };
    private Runnable snackAction;

    // --------------------------------------------------------------- ciclo de vida

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        prefs = getSharedPreferences("folio", MODE_PRIVATE);
        setupWindow();
        buildUi();
        if (saved != null) {
            restore(saved);
        } else {
            handleShare(getIntent());
        }
        updateChrome(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        List<Page> pages = grid.pages();
        ArrayList<Uri> uris = new ArrayList<>(pages.size());
        int[] rot = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            uris.add(pages.get(i).uri);
            rot[i] = pages.get(i).rotation;
        }
        out.putParcelableArrayList("uris", uris);
        out.putIntArray("rotations", rot);
    }

    @SuppressWarnings("deprecation")
    private void restore(Bundle saved) {
        ArrayList<Parcelable> uris = saved.getParcelableArrayList("uris");
        int[] rot = saved.getIntArray("rotations");
        if (uris == null) return;
        ArrayList<Page> pages = new ArrayList<>();
        for (int i = 0; i < uris.size(); i++) {
            if (!(uris.get(i) instanceof Uri)) continue;
            Page p = new Page((Uri) uris.get(i));
            if (rot != null && i < rot.length) p.rotation = rot[i];
            pages.add(p);
        }
        addPages(pages);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (sheet != null) {
            root.post(new Runnable() {
                @Override
                public void run() {
                    if (sheet != null) sheet.updateLayout();
                }
            });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (sheet != null) {
            if (sheet.isCancelable()) sheet.dismiss();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loader.shutdownNow();
        worker.shutdown();
    }

    // --------------------------------------------------------------- ventana

    @SuppressWarnings("deprecation")
    private void setupWindow() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        w.setStatusBarColor(0x00000000);
        w.setNavigationBarColor(0x00000000);
        w.setNavigationBarContrastEnforced(false);
        w.setStatusBarContrastEnforced(false);
    }

    @SuppressWarnings("deprecation")
    private void applyInsets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = in.getInsets(WindowInsets.Type.ime());
            insetTop = bars.top;
            insetBottom = bars.bottom;
            insetLeft = bars.left;
            insetRight = bars.right;
            insetIme = ime.bottom;
        } else {
            insetTop = in.getSystemWindowInsetTop();
            insetBottom = in.getSystemWindowInsetBottom();
            insetLeft = in.getSystemWindowInsetLeft();
            insetRight = in.getSystemWindowInsetRight();
            insetIme = 0;
        }
        header.setPadding(insetLeft + Ui.dp(22), insetTop + Ui.dp(18), insetRight + Ui.dp(12), Ui.dp(14));
        bottomBar.setPadding(insetLeft + Ui.dp(16), Ui.dp(36), insetRight + Ui.dp(16), insetBottom + Ui.dp(16));
        grid.setPadding(insetLeft + Ui.dp(16), Ui.dp(6), insetRight + Ui.dp(16), grid.getPaddingBottom());
        empty.setPadding(insetLeft + Ui.dp(32), 0, insetRight + Ui.dp(32), Ui.dp(120) + insetBottom);
        if (sheet != null) sheet.updateLayout();
    }

    @Override
    public FrameLayout sheetRoot() {
        return root;
    }

    @Override
    public int sheetBottomInset() {
        return Math.max(insetBottom, insetIme);
    }

    @Override
    public void onSheetShown(Sheet s) {
        if (sheet != null && sheet != s) sheet.dismiss();
        sheet = s;
        hideSnack();
    }

    @Override
    public void onSheetClosed(Sheet s) {
        if (sheet == s) sheet = null;
        hideKeyboard();
    }

    // --------------------------------------------------------------- interfaz

    private void buildUi() {
        Context c = this;
        root = new FrameLayout(c);
        root.setBackgroundColor(Ui.BG);

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // Cabecera.
        header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(c, "Folio", 34, Ui.TEXT, 700);
        title.setLetterSpacing(-0.02f);
        titles.addView(title);
        subtitle = Ui.text(c, "", 14, Ui.TEXT_2, 450);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = Ui.lin(Ui.WRAP, Ui.WRAP);
        slp.topMargin = Ui.dp(6);
        titles.addView(subtitle, slp);
        header.addView(titles, Ui.lin(0, Ui.WRAP, 1f));
        moreButton = Ui.iconButton(c, Icon.MORE, Ui.TEXT, "Más opciones");
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        header.addView(moreButton, Ui.lin(Ui.dp(48), Ui.dp(48)));
        column.addView(header, Ui.lin(Ui.MATCH, Ui.WRAP));

        divider = new View(c);
        divider.setBackgroundColor(Ui.STROKE);
        divider.setAlpha(0f);
        column.addView(divider, Ui.lin(Ui.MATCH, Math.max(1, Ui.dp(0.7f))));

        // Cuerpo: lista, estado vacío y botones flotantes.
        FrameLayout body = new FrameLayout(c);
        column.addView(body, Ui.lin(Ui.MATCH, 0, 1f));

        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        grid = new PageGrid(c);
        grid.setListener(this);
        scroll.addView(grid, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                divider.setAlpha(Math.min(1f, y / (float) Ui.dp(24)));
            }
        });
        body.addView(scroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        empty = new LinearLayout(c);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.addView(new EmptyArt(c));
        TextView et = Ui.text(c, "Junta tus imágenes en un PDF", 22, Ui.TEXT, 650);
        et.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams etl = Ui.lin(Ui.WRAP, Ui.WRAP);
        etl.topMargin = Ui.dp(28);
        empty.addView(et, etl);
        TextView ed = Ui.text(c, "Elige fotos, ordénalas arrastrándolas y crea un único PDF en un momento.",
                15, Ui.TEXT_2, 400);
        ed.setGravity(Gravity.CENTER);
        ed.setLineSpacing(Ui.dp(4), 1f);
        ed.setMaxWidth(Ui.dp(300));
        LinearLayout.LayoutParams edl = Ui.lin(Ui.WRAP, Ui.WRAP);
        edl.topMargin = Ui.dp(10);
        empty.addView(ed, edl);
        body.addView(empty, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        bottomBar = new LinearLayout(c);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000B0B0E, 0xE60B0B0E, Ui.BG, Ui.BG}));
        addButton = Ui.button(c, "Añadir imágenes", Icon.PLUS, Ui.PRIMARY);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddSheet();
            }
        });
        createButton = Ui.button(c, "Crear PDF", Icon.FILE, Ui.PRIMARY);
        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportSheet();
            }
        });
        bottomBar.addView(addButton, Ui.lin(0, Ui.dp(56), 1f));
        LinearLayout.LayoutParams clp = Ui.lin(0, Ui.dp(56), 1f);
        clp.leftMargin = Ui.dp(10);
        bottomBar.addView(createButton, clp);
        body.addView(bottomBar, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));
        bottomBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = b - t;
                if (grid.getPaddingBottom() != h + Ui.dp(8)) {
                    grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(), h + Ui.dp(8));
                }
                grid.setBottomOverlay(h - Ui.dp(24));
                positionSnack();
            }
        });

        // Aviso flotante (deshacer, errores…).
        snack = Ui.text(c, "", 14, Ui.TEXT, 500);
        snack.setBackground(Ui.rounded(Ui.SURFACE_3, 16));
        snack.setPadding(Ui.dp(18), Ui.dp(15), Ui.dp(18), Ui.dp(15));
        snack.setGravity(Gravity.CENTER_VERTICAL);
        snack.setElevation(Ui.dp(6));
        snack.setVisibility(View.GONE);
        snack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Runnable r = snackAction;
                hideSnack();
                if (r != null) r.run();
            }
        });
        FrameLayout.LayoutParams snl = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        snl.leftMargin = Ui.dp(16);
        snl.rightMargin = Ui.dp(16);
        root.addView(snack, snl);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                applyInsets(in);
                return in;
            }
        });
        setContentView(root);
    }

    /** Actualiza textos y botones según haya páginas o no. */
    private void updateChrome(boolean animate) {
        int n = grid.count();
        boolean has = n > 0;
        if (!has) {
            subtitle.setText("Imágenes a PDF");
        } else {
            subtitle.setText((n == 1 ? "1 página" : n + " páginas")
                    + (n > 1 ? " · mantén pulsado para mover" : ""));
        }
        moreButton.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        boolean wasEmpty = empty.getVisibility() == View.VISIBLE;
        if (has && wasEmpty) {
            if (animate) {
                empty.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (grid.count() > 0) empty.setVisibility(View.GONE);
                    }
                }).start();
            } else {
                empty.setVisibility(View.GONE);
            }
        } else if (!has && !wasEmpty) {
            empty.setVisibility(View.VISIBLE);
            empty.setAlpha(0f);
            empty.animate().alpha(1f).setDuration(220).start();
        }
        if (has) {
            Ui.style(addButton, "", Icon.PLUS, Ui.SECONDARY);
            addButton.setContentDescription("Añadir imágenes");
            addButton.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
            addButton.setLayoutParams(Ui.lin(Ui.dp(56), Ui.dp(56)));
            createButton.setVisibility(View.VISIBLE);
        } else {
            Ui.style(addButton, "Añadir imágenes", Icon.PLUS, Ui.PRIMARY);
            addButton.setPadding(Ui.dp(20), 0, Ui.dp(22), 0);
            addButton.setLayoutParams(Ui.lin(0, Ui.dp(56), 1f));
            createButton.setVisibility(View.GONE);
        }
    }

    // --------------------------------------------------------------- añadir imágenes

    private void showAddSheet() {
        new Sheet(this, this)
                .title("Añadir imágenes")
                .space(12)
                .action(Icon.PHOTOS, "Galería", "Tus fotos y álbumes", false, new Runnable() {
                    @Override
                    public void run() {
                        pickPhotos();
                    }
                })
                .action(Icon.FOLDER, "Archivos", "Descargas, otras carpetas o la nube", false, new Runnable() {
                    @Override
                    public void run() {
                        pickFiles();
                    }
                })
                .show();
    }

    private void pickPhotos() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 33) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES);
            i.setType("image/*");
            i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
            if (Build.VERSION.SDK_INT >= 35) i.putExtra(MediaStore.EXTRA_PICK_IMAGES_IN_ORDER, true);
        } else {
            i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        launch(i, REQ_PHOTOS);
    }

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        launch(i, REQ_FILES);
    }

    @SuppressWarnings("deprecation")
    private void launch(Intent i, int request) {
        try {
            startActivityForResult(i, request);
        } catch (ActivityNotFoundException e) {
            if (request == REQ_PHOTOS) {
                pickFiles();
            } else {
                showSnack("No hay ninguna app para elegir imágenes.", null, null);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (request == REQ_FILES) {
            for (Uri u : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // No todos los proveedores lo permiten; el permiso temporal basta.
                }
            }
        }
        addUris(uris);
    }

    @SuppressWarnings("deprecation")
    private void handleShare(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Parcelable p = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (p instanceof Uri) uris.add((Uri) p);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Parcelable> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) for (Parcelable p : list) if (p instanceof Uri) uris.add((Uri) p);
        } else {
            return;
        }
        if (uris.isEmpty() && intent.getClipData() != null) {
            ClipData clip = intent.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        }
        // Que una rotación de pantalla no vuelva a añadirlas.
        intent.setAction(Intent.ACTION_MAIN);
        addUris(uris);
    }

    private void addUris(List<Uri> uris) {
        ArrayList<Page> pages = new ArrayList<>();
        for (Uri u : uris) pages.add(new Page(u));
        addPages(pages);
        if (!pages.isEmpty()) {
            final boolean wasEmpty = grid.count() == pages.size();
            if (!wasEmpty) {
                // Lleva la vista hasta las nuevas.
                scroll.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scroll.smoothScrollTo(0, grid.getHeight());
                    }
                }, 120);
            }
        }
    }

    private void addPages(List<Page> pages) {
        if (pages.isEmpty()) return;
        int i = 0;
        for (Page p : pages) {
            grid.add(p, i++);
            loadThumb(p);
        }
        updateChrome(true);
    }

    private void loadThumb(final Page p) {
        final android.content.ContentResolver cr = getContentResolver();
        loader.execute(new Runnable() {
            @Override
            public void run() {
                Images.readMeta(cr, p);
                android.graphics.Bitmap b = null;
                try {
                    b = Images.thumbnail(cr, p.uri, THUMB_SIDE);
                } catch (Throwable t) {
                    b = null;
                }
                final android.graphics.Bitmap thumb = b;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed()) return;
                        PageCell cell = grid.cellFor(p);
                        if (thumb != null) {
                            p.thumb = thumb;
                            if (cell != null) cell.onThumbReady();
                        } else {
                            p.failed = true;
                            if (cell != null) {
                                grid.remove(cell);
                                updateChrome(true);
                            }
                            failedLoads++;
                            main.removeCallbacks(reportFailures);
                            main.postDelayed(reportFailures, 300);
                        }
                    }
                });
            }
        });
    }

    private final Runnable reportFailures = new Runnable() {
        @Override
        public void run() {
            int n = failedLoads;
            failedLoads = 0;
            if (n <= 0) return;
            showSnack(n == 1 ? "No se pudo abrir 1 imagen." : "No se pudieron abrir " + n + " imágenes.", null, null);
        }
    };

    // --------------------------------------------------------------- PageGrid.Listener

    @Override
    public void onCellTap(PageCell cell) {
        showPageSheet(cell);
    }

    @Override
    public void onCellRemove(PageCell cell) {
        removeWithUndo(cell);
    }

    @Override
    public void onOrderChanged() {
        // El orden vive en la cuadrícula; no hay nada más que guardar.
    }

    @Override
    public void onDragState(boolean dragging) {
        if (dragging) hideSnack();
    }

    private void removeWithUndo(PageCell cell) {
        final Page p = cell.page;
        final int index = grid.indexOf(cell);
        grid.remove(cell);
        updateChrome(true);
        showSnack("Imagen quitada", "Deshacer", new Runnable() {
            @Override
            public void run() {
                grid.insert(p, index, 0);
                updateChrome(true);
            }
        });
    }

    // --------------------------------------------------------------- hoja de una página

    private void showPageSheet(final PageCell cell) {
        final Page p = cell.page;
        int index = grid.indexOf(cell);
        if (index < 0) return;
        final Sheet s = new Sheet(this, this);
        final PageCell preview = new PageCell(this, p, false);
        LinearLayout.LayoutParams plp = Ui.lin(Ui.MATCH, Ui.dp(250));
        plp.bottomMargin = Ui.dp(18);
        s.body.addView(preview, plp);
        s.title("Página " + (index + 1) + " de " + grid.count());
        if (p.name != null) {
            s.subtitle(p.name);
        }
        s.space(18);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(tile(Icon.ROTATE, "Girar", false, new Runnable() {
            @Override
            public void run() {
                p.rotation = (p.rotation + 90) % 360;
                cell.animateRotation();
                preview.animateRotation();
            }
        }), Ui.lin(0, Ui.WRAP, 1f));
        row.addView(gapView(), Ui.lin(Ui.dp(8), 1));
        row.addView(tile(Icon.TO_START, "Al principio", false, new Runnable() {
            @Override
            public void run() {
                s.dismiss();
                grid.move(cell, 0);
                scroll.smoothScrollTo(0, 0);
            }
        }), Ui.lin(0, Ui.WRAP, 1f));
        row.addView(gapView(), Ui.lin(Ui.dp(8), 1));
        row.addView(tile(Icon.TO_END, "Al final", false, new Runnable() {
            @Override
            public void run() {
                s.dismiss();
                grid.move(cell, grid.count() - 1);
                scroll.smoothScrollTo(0, grid.getHeight());
            }
        }), Ui.lin(0, Ui.WRAP, 1f));
        row.addView(gapView(), Ui.lin(Ui.dp(8), 1));
        row.addView(tile(Icon.TRASH, "Quitar", true, new Runnable() {
            @Override
            public void run() {
                s.dismiss();
                removeWithUndo(cell);
            }
        }), Ui.lin(0, Ui.WRAP, 1f));
        s.add(row);
        s.show();
    }

    private View gapView() {
        return new View(this);
    }

    private View tile(int icon, String label, boolean danger, final Runnable r) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(4), Ui.dp(14), Ui.dp(4), Ui.dp(12));
        t.setBackground(Ui.pressable(Ui.rounded(Ui.SURFACE_2, 18), 18, 0x22FFFFFF));
        t.setClickable(true);
        int color = danger ? Ui.DANGER : Ui.TEXT;
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icon(icon, color, 22));
        t.addView(iv, Ui.lin(Ui.dp(24), Ui.dp(24)));
        TextView tv = Ui.text(this, label, 12, danger ? Ui.DANGER : Ui.TEXT_2, 550);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = Ui.lin(Ui.WRAP, Ui.WRAP);
        lp.topMargin = Ui.dp(8);
        t.addView(tv, lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        return t;
    }

    // --------------------------------------------------------------- menú

    private void showMenu() {
        new Sheet(this, this)
                .title("Ordenar")
                .space(12)
                .action(Icon.REVERSE, "Invertir orden", "La última pasa a ser la primera", false, new Runnable() {
                    @Override
                    public void run() {
                        grid.reverse();
                    }
                })
                .action(Icon.SORT_NAME, "Por nombre", "Del 1 al 9 y de la A a la Z", false, new Runnable() {
                    @Override
                    public void run() {
                        grid.sort(new Comparator<Page>() {
                            @Override
                            public int compare(Page a, Page b) {
                                return Images.naturalCompare(a.name, b.name);
                            }
                        });
                    }
                })
                .action(Icon.SORT_DATE, "Por fecha", "De la más antigua a la más reciente", false, new Runnable() {
                    @Override
                    public void run() {
                        grid.sort(new Comparator<Page>() {
                            @Override
                            public int compare(Page a, Page b) {
                                return Long.compare(a.date, b.date);
                            }
                        });
                    }
                })
                .action(Icon.TRASH, "Quitar todas", null, true, new Runnable() {
                    @Override
                    public void run() {
                        clearWithUndo();
                    }
                })
                .show();
    }

    private void clearWithUndo() {
        final List<Page> pages = grid.pages();
        if (pages.isEmpty()) return;
        grid.clear();
        updateChrome(true);
        showSnack(pages.size() == 1 ? "Se quitó 1 imagen" : "Se quitaron " + pages.size() + " imágenes",
                "Deshacer", new Runnable() {
                    @Override
                    public void run() {
                        int i = 0;
                        for (Page p : pages) grid.add(p, i++);
                        updateChrome(true);
                    }
                });
    }

    // --------------------------------------------------------------- exportar

    private void showExportSheet() {
        final List<Page> pages = grid.pages();
        if (pages.isEmpty()) return;
        final Sheet s = new Sheet(this, this);
        s.title("Crear PDF");
        s.subtitle(pages.size() == 1 ? "1 página" : pages.size() + " páginas");
        s.space(20);

        s.add(Ui.label(this, "Nombre"));
        s.space(8);
        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.HORIZONTAL);
        nameBox.setGravity(Gravity.CENTER_VERTICAL);
        nameBox.setBackground(Ui.rounded(Ui.SURFACE_2, 16));
        nameBox.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
        final EditText name = new EditText(this);
        name.setText(defaultName());
        name.setTextColor(Ui.TEXT);
        name.setHintTextColor(Ui.TEXT_3);
        name.setHint("Nombre del PDF");
        name.setTextSize(16);
        name.setTypeface(Ui.font(500));
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        name.setBackground(null);
        name.setPadding(0, Ui.dp(16), 0, Ui.dp(16));
        name.setSelectAllOnFocus(true);
        name.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                hideKeyboard();
                name.clearFocus();
                return true;
            }
        });
        nameBox.addView(name, Ui.lin(0, Ui.WRAP, 1f));
        nameBox.addView(Ui.text(this, ".pdf", 16, Ui.TEXT_3, 500));
        s.add(nameBox);
        s.space(18);

        s.add(Ui.label(this, "Tamaño de página"));
        s.space(8);
        final Segmented size = new Segmented(this, new String[]{"A4", "Carta", "Como la imagen"},
                prefs.getInt("size", PdfBuilder.SIZE_A4), null);
        s.add(size);
        s.space(18);

        s.add(Ui.label(this, "Márgenes"));
        s.space(8);
        final Segmented margin = new Segmented(this, new String[]{"Sin márgenes", "Con márgenes"},
                prefs.getBoolean("margin", false) ? 1 : 0, null);
        s.add(margin);
        s.space(18);

        s.add(Ui.label(this, "Calidad"));
        s.space(8);
        final Segmented quality = new Segmented(this, new String[]{"Alta", "Media", "Ligera"},
                prefs.getInt("quality", 1), null);
        s.add(quality);
        s.space(24);

        LinearLayout go = Ui.button(this, "Crear PDF", Icon.FILE, Ui.PRIMARY);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                String n = sanitize(name.getText().toString());
                prefs.edit()
                        .putInt("size", size.selected())
                        .putBoolean("margin", margin.selected() == 1)
                        .putInt("quality", quality.selected())
                        .apply();
                startExport(s, pages, n, size.selected(), margin.selected() == 1, quality.selected());
            }
        });
        s.add(go);
        s.show();
    }

    private String defaultName() {
        return "Folio " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(new Date());
    }

    private String sanitize(String s) {
        s = s.trim();
        if (s.toLowerCase(Locale.ROOT).endsWith(".pdf")) s = s.substring(0, s.length() - 4).trim();
        s = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        while (s.startsWith(".")) s = s.substring(1);
        if (s.isEmpty()) s = defaultName();
        if (s.length() > 120) s = s.substring(0, 120);
        return s;
    }

    private void startExport(final Sheet s, List<Page> pages, final String name, int size, boolean margin,
                             int quality) {
        s.cancelable(false);
        s.clear();
        s.title("Creando PDF…");
        final TextView status = Ui.text(this, "Preparando…", 14, Ui.TEXT_2, 400);
        LinearLayout.LayoutParams stl = Ui.lin(Ui.MATCH, Ui.WRAP);
        stl.topMargin = Ui.dp(6);
        s.body.addView(status, stl);
        s.space(22);
        final ProgressLine bar = new ProgressLine(this);
        s.body.addView(bar, Ui.lin(Ui.MATCH, Ui.dp(8)));
        s.space(24);
        LinearLayout cancel = Ui.button(this, "Cancelar", 0, Ui.SECONDARY);
        s.add(cancel);

        exporter = new Exporter(getContentResolver(), pages, name, size, margin, quality, new Exporter.Callback() {
            @Override
            public void onProgress(int done, int total) {
                bar.setProgress(total == 0 ? 0f : done / (float) total);
                status.setText(done >= total ? "Guardando…" : "Página " + (done + 1) + " de " + total);
            }

            @Override
            public void onDone(Uri uri, String fileName, long bytes, int skipped) {
                exporter = null;
                showDone(s, uri, fileName, bytes, skipped);
            }

            @Override
            public void onError(String message) {
                exporter = null;
                showError(s, message);
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (exporter != null) exporter.cancel();
                exporter = null;
                s.cancelable(true);
                s.dismiss();
                showSnack("Cancelado", null, null);
            }
        });
        worker.execute(exporter);
    }

    private void showDone(final Sheet s, final Uri uri, String fileName, long bytes, int skipped) {
        s.cancelable(true);
        s.clear();
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.rounded(Ui.ACCENT, 28));
        ImageView ok = new ImageView(this);
        ok.setImageDrawable(new Icon(Icon.CHECK, Ui.ON_ACCENT, 28, 2.4f));
        ok.setScaleType(ImageView.ScaleType.CENTER);
        badge.addView(ok, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout.LayoutParams blp = Ui.lin(Ui.dp(56), Ui.dp(56));
        blp.bottomMargin = Ui.dp(18);
        s.body.addView(badge, blp);
        badge.setScaleX(0.6f);
        badge.setScaleY(0.6f);
        badge.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();

        s.title("PDF listo");
        String info = fileName + " · " + Formatter.formatShortFileSize(this, bytes)
                + "\nGuardado en Descargas/" + Exporter.FOLDER;
        if (skipped > 0) {
            info += "\n" + (skipped == 1 ? "1 imagen no se pudo leer y se ha saltado."
                    : skipped + " imágenes no se pudieron leer y se han saltado.");
        }
        s.subtitle(info);
        s.space(24);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout open = Ui.button(this, "Abrir", Icon.OPEN, Ui.SECONDARY);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPdf(uri);
            }
        });
        LinearLayout share = Ui.button(this, "Compartir", Icon.SHARE, Ui.PRIMARY);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sharePdf(uri);
            }
        });
        row.addView(open, Ui.lin(0, Ui.dp(56), 1f));
        LinearLayout.LayoutParams slp = Ui.lin(0, Ui.dp(56), 1f);
        slp.leftMargin = Ui.dp(10);
        row.addView(share, slp);
        s.add(row);
        s.space(8);
        LinearLayout done = Ui.button(this, "Hecho", 0, Ui.GHOST);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.dismiss();
            }
        });
        s.add(done);
    }

    private void showError(final Sheet s, String message) {
        s.cancelable(true);
        s.clear();
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.rounded(0x33FF6B6B, 28));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icon(Icon.ALERT, Ui.DANGER, 28));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        badge.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout.LayoutParams blp = Ui.lin(Ui.dp(56), Ui.dp(56));
        blp.bottomMargin = Ui.dp(18);
        s.body.addView(badge, blp);
        s.title("No se pudo crear el PDF");
        s.subtitle(message);
        s.space(24);
        LinearLayout close = Ui.button(this, "Cerrar", 0, Ui.SECONDARY);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.dismiss();
            }
        });
        s.add(close);
    }

    private void openPdf(Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            showSnack("No hay ninguna app para abrir PDF.", null, null);
        }
    }

    private void sharePdf(Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri("", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, null));
        } catch (ActivityNotFoundException e) {
            showSnack("No hay ninguna app para compartir.", null, null);
        }
    }

    // --------------------------------------------------------------- aviso flotante

    private void showSnack(String msg, String action, Runnable onAction) {
        main.removeCallbacks(hideSnack);
        snackAction = onAction;
        if (action != null) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(msg);
            sb.append("     ");
            int start = sb.length();
            sb.append(action);
            sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.ACCENT), start, sb.length(), 0);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(), 0);
            snack.setText(sb);
        } else {
            snack.setText(msg);
        }
        positionSnack();
        if (snack.getVisibility() != View.VISIBLE) {
            snack.setVisibility(View.VISIBLE);
            snack.setAlpha(0f);
            snack.setTranslationY(Ui.dp(16));
        }
        snack.animate().alpha(1f).translationY(0f).setDuration(200).start();
        main.postDelayed(hideSnack, action != null ? 5000 : 3000);
    }

    private void hideSnack() {
        main.removeCallbacks(hideSnack);
        snackAction = null;
        if (snack.getVisibility() != View.VISIBLE) return;
        snack.animate().alpha(0f).translationY(Ui.dp(16)).setDuration(180).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (snack.getAlpha() == 0f) snack.setVisibility(View.GONE);
            }
        }).start();
    }

    private void positionSnack() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) snack.getLayoutParams();
        int bottom = Math.max(bottomBar.getHeight() - Ui.dp(28), insetBottom + Ui.dp(80)) + Ui.dp(4);
        if (lp.bottomMargin != bottom) {
            lp.bottomMargin = bottom;
            lp.leftMargin = insetLeft + Ui.dp(16);
            lp.rightMargin = insetRight + Ui.dp(16);
            snack.setLayoutParams(lp);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (imm != null) imm.hideSoftInputFromWindow(f != null ? f.getWindowToken() : root.getWindowToken(), 0);
    }

    /** Barra de progreso fina con el color de acento. */
    static final class ProgressLine extends View {
        private final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private float progress;
        private android.animation.ValueAnimator anim;

        ProgressLine(Context c) {
            super(c);
            track.setColor(Ui.SURFACE_2);
            fill.setColor(Ui.ACCENT);
        }

        void setProgress(float p) {
            if (anim != null) anim.cancel();
            anim = android.animation.ValueAnimator.ofFloat(progress, Math.max(0f, Math.min(1f, p)));
            anim.setDuration(260);
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator va) {
                    progress = (Float) va.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float h = getHeight(), r = h / 2f;
            rect.set(0, 0, getWidth(), h);
            c.drawRoundRect(rect, r, r, track);
            if (progress > 0f) {
                rect.set(0, 0, Math.max(h, getWidth() * progress), h);
                c.drawRoundRect(rect, r, r, fill);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideKeyboard();
    }
}
