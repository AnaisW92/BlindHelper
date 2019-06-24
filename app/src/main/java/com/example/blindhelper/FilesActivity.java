package com.example.blindhelper;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.ListActivity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class FilesActivity extends ListActivity {

    private ListView mList = null;
    private TextView mEmpty = null;
    private File mCurrentFile = null ;
    private FileAdapter mAdapter = null ;
    private boolean mCountdown = false ;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // On récupère la ListView de notre activité
        mList = (ListView) getListView();

        // On vérifie que le répertoire externe est bien accessible
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            // S'il ne l'est pas, on affiche un message
            mEmpty = (TextView) mList.getEmptyView();
            mEmpty.setText("Vous ne pouvez pas accéder aux fichiers");
        } else {
            // S'il l'est, on déclare qu'on veut un menu contextuel sur les éléments de la liste
            registerForContextMenu(mList);


            // On récupère la racine de la carte SD pour qu'elle soit le répertoire consulté au départ
            mCurrentFile = Environment.getExternalStorageDirectory();


            // On change le titre de l'activité pour y mettre le chemin actuel
            setTitle(mCurrentFile.getAbsolutePath());

            // On récupère la liste des fichiers dans le répertoire actuel
            File[] fichiers = mCurrentFile.listFiles();

            // On transforme le tableau en une structure de données de taille variable
            ArrayList<File> liste = new ArrayList<File>();
            for (File f : fichiers)
                liste.add(f);

            mAdapter = new FileAdapter(this, android.R.layout.simple_list_item_1, liste);
            // On ajoute l'adaptateur à la liste
            mList.setAdapter(mAdapter);
            // On trie la liste
            mAdapter.sort();

            // On ajoute un Listener sur les items de la liste
            mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                // Que se passe-t-il en cas de clic sur un élément de la liste ?
                public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
                    File fichier = mAdapter.getItem(position);
                    // Si le fichier est un répertoire…
                    if(fichier.isDirectory())
                        // On change de répertoire courant
                        updateDirectory(fichier);
                    else
                        // Sinon, on lance l'item
                        seeItem(fichier);
                }
            });


        }
    }

    

    /**
     * Utilisé pour visualiser un fichier
     * @param pFile le fichier à visualiser
     */
    private void seeItem(File pFile) {
        // On crée un intent
        Intent i = new Intent(Intent.ACTION_VIEW);

        String ext = pFile.getName().substring(pFile.getName().indexOf(".") + 1).toLowerCase();
        if(ext.equals("txt"))
            i.setDataAndType(Uri.fromFile(pFile), "txt");
        /** Faites en autant que vous le désirez */



        try {
            startActivity(i);
            // Et s'il n'y a pas d'activité qui puisse gérer ce type de fichier
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Oups, vous n'avez pas d'application qui puisse lancer ce fichier", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    /**
     * On enlève tous les éléments de la liste
     */

    public void setEmpty() {
        // Si l'adaptateur n'est pas vide…
        if(!mAdapter.isEmpty())
            // Alors on le vide !
            mAdapter.clear();
    }


    /**
     * Utilisé pour naviguer entre les répertoires
     * @param pFile le nouveau répertoire dans lequel aller
     */

    public void updateDirectory(File pFile) {
        // On change le titre de l'activité
        setTitle(pFile.getAbsolutePath());

        // L'utilisateur ne souhaite plus sortir de l'application
        mCountdown = false;

        // On change le répertoire actuel
        mCurrentFile = pFile;
        // On vide les répertoires actuels
        setEmpty();

        // On récupère la liste des fichiers du nouveau répertoire
        File[] fichiers = mCurrentFile.listFiles();

        // Si le répertoire n'est pas vide…
        if(fichiers != null) {
            // On les ajoute à  l'adaptateur
            for(File f : fichiers)
                mAdapter.add(f);
            // Puis on le trie
            mAdapter.sort();
        }
    }


}
