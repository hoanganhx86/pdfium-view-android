package com.shockwave.pdfiumtest;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.shockwave.pdfium.PdfView;

import java.io.IOException;

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
        pdf.loadDocument(fileUri);
    }

}
