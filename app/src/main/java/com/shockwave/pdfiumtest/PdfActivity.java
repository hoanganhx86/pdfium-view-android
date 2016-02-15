package com.shockwave.pdfiumtest;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.shockwave.pdfium.PdfView;
import com.shockwave.pdfium.listener.OnErrorOccurredListener;
import com.shockwave.pdfium.listener.OnPageChangedListener;

/**
 * Created by dudu on 14-02-2016.
 */
public class PdfActivity extends AppCompatActivity {
    PdfView pdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);
        pdf = (PdfView) findViewById(R.id.pdfium_view);
        final TextView pages = (TextView) findViewById(R.id.pdf_pages);
        Intent intent = getIntent();
        Uri fileUri;
        if( (fileUri = intent.getData()) == null){
            finish();
            return ;
        }
        pdf.fromUri(fileUri)
                .onErrorOccured(new OnErrorOccurredListener() {

                    @Override
                    public void errorOccured() {
                        Log.d("hola", "funca? :c");
                        Toast.makeText(PdfActivity.this, "Error leyendo archivo", Toast.LENGTH_LONG);
                    }
                })
                .onPageChanged(new OnPageChangedListener() {
                    @Override
                    public void pageChanged(int page, int pageCount) {
                        String actualPage = "" + page + "/" + pageCount;
                        pages.setText(actualPage);
                        pages.refreshDrawableState();
                        Log.d("Texto cambiado", "Yes");
                    }
                })
                .load();
                }

    }
