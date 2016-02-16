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
import com.shockwave.pdfium.listener.OnLoadCompleteListener;
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
                        Log.d("PdfActivirtListener", "An error occured.");
                    }
                })
                .onPageChanged(new OnPageChangedListener() {
                    @Override
                    public void pageChanged(int page, int pageCount) {
                        Log.d("PdfActivityListener", "Page changed.");
                    }
                })
                .onLoad(new OnLoadCompleteListener() {
                    @Override
                    public void loadComplete(int nbPages) {
                        Log.d("PdfActivityListener","Load complete.");
                    }
                })
                .load();
                }

    }
