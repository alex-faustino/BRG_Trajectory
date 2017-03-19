package com.projecttango.examples.java.helloareadescription;

import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoErrorException;

import java.io.File;
import java.util.ArrayList;

import static android.app.Activity.RESULT_OK;

public class SelectAdfActivity extends AppCompatActivity {
// // TODO: 2/16/17 mimic AdfUuidListViewActivity to allow ADF selection
    private String[] mSelectMenuStrings;
    private ListView mAdfListView;
    private ArrayList<AdfData> mAdfDataList;
    private AdfUuidArrayAdapter mAdfListAdapter;
    private String mAppSpaceAdfFolder;
    private Tango mTango;
    private volatile boolean mIsTangoReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get layout and menu strings from resources
        setContentView(R.layout.activity_select_adf);
        mSelectMenuStrings = getResources().getStringArray(
                R.array.set_dialog_menu_items_select_adf);

        // Get ListView ready
        mAdfListView = (ListView) findViewById(R.id.uuid_list_view_tango_space);
        mAdfDataList = new ArrayList<AdfData>();
        mAdfListAdapter = new AdfUuidArrayAdapter(this, mAdfDataList);
        mAdfListView.setAdapter(mAdfListAdapter);
        registerForContextMenu(mAdfListView);

        mAppSpaceAdfFolder = getAppSpaceAdfFolder();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Initialize Tango Service as a normal Android Service, since we call
        // mTango.disconnect() in onPause, this will unbind Tango Service, so
        // everytime when onResume gets called, we should create a new Tango object.
        mTango = new Tango(SelectAdfActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready,
            // this Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only
            // when there is no UI thread changes involved.
            @Override
            public void run() {
                mIsTangoReady = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (SelectAdfActivity.this) {
                            updateList();
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (this) {
            // Unbinds Tango Service
            mTango.disconnect();
        }
        mIsTangoReady = false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (v.getId() == R.id.uuid_list_view_tango_space) {
            menu.setHeaderTitle(mAdfDataList.get(info.position).uuid);
            menu.add(mSelectMenuStrings[0]);
            menu.add(mSelectMenuStrings[1]);
            menu.add(mSelectMenuStrings[2]);
            menu.add(mSelectMenuStrings[3]);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!mIsTangoReady) {
            Toast.makeText(this, R.string.tango_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        String itemName = (String) item.getTitle();
        int index = info.position;

        // Calls functions to handle menu items
        if (itemName.equals(mSelectMenuStrings[0])) {
            // Rename the ADF and update ADF ListView
            showSetNameDialog(mAdfDataList.get(index).uuid);
        } else if (itemName.equals(mSelectMenuStrings[1])) {
            // Delete the ADF from Tango space and update the Tango ADF Listview.
            deleteAdfFromTangoSpace(mAdfDataList.get(index).uuid);
        } else if (itemName.equals(mSelectMenuStrings[2])) {
            // Export the ADF into application package folder and update the Listview.
            exportAdf(mAdfDataList.get(index).uuid);
        } else if (itemName.equals(mSelectMenuStrings[3])) {
            // Return ADF to HelloAreaDescriptionActivity as Intent Extra
            loadAdf(mAdfDataList.get(index).uuid);
        }

        updateList();
        return true;
    }

    /**
     * Export an ADF from Tango space to app space.
     */
    private void exportAdf(String uuid) {
        try {
            mTango.exportAreaDescriptionFile(uuid, mAppSpaceAdfFolder);
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.adf_exists_app_space, Toast.LENGTH_SHORT).show();
        }
    }

    public void deleteAdfFromTangoSpace(String uuid) {
        try {
            mTango.deleteAreaDescription(uuid);
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.no_uuid_tango_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Returns maps storage location in the App package folder. Creates a folder called Maps, if it
     * does not exist.
     */
    private String getAppSpaceAdfFolder() {
        String mapsFolder = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + "Maps";
        File file = new File(mapsFolder);
        if (!file.exists()) {
            file.mkdirs();
        }
        return mapsFolder;
    }

    /**
     * Updates the list of AdfData corresponding to the Tango space.
     */
    private void updateList() {
        ArrayList<String> fullUuidList;
        TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();

        try {
            // Get all ADF UUIDs.
            fullUuidList = mTango.listAreaDescriptions();
            // Get the names from the UUIDs.
            mAdfDataList.clear();
            for (String uuid : fullUuidList) {
                String name;
                try {
                    metadata = mTango.loadAreaDescriptionMetaData(uuid);
                } catch (TangoErrorException e) {
                    Toast.makeText(this, R.string.tango_error, Toast.LENGTH_SHORT).show();
                }
                name = new String(metadata.get(TangoAreaDescriptionMetaData.KEY_NAME));
                mAdfDataList.add(new AdfData(uuid, name));
            }
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.tango_error, Toast.LENGTH_SHORT).show();
        }

        mAdfListAdapter.setAdfData(mAdfDataList);
        mAdfListAdapter.notifyDataSetChanged();
    }

    private void showSetNameDialog(String mCurrentUuid) {
        if (!mIsTangoReady) {
            Toast.makeText(this, R.string.tango_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle bundle = new Bundle();
        TangoAreaDescriptionMetaData metaData = mTango.loadAreaDescriptionMetaData(mCurrentUuid);
        byte[] adfNameBytes = metaData.get(TangoAreaDescriptionMetaData.KEY_NAME);
        if (adfNameBytes != null) {
            String fillDialogName = new String(adfNameBytes);
            bundle.putString(TangoAreaDescriptionMetaData.KEY_NAME, fillDialogName);
        }
        bundle.putString(TangoAreaDescriptionMetaData.KEY_UUID, mCurrentUuid);
        FragmentManager manager = getFragmentManager();
        SetAdfNameDialog setAdfNameDialog = new SetAdfNameDialog();
        setAdfNameDialog.setArguments(bundle);
        setAdfNameDialog.show(manager, "ADFNameDialog");
    }

    private void loadAdf(String uuid) {
        Intent returnSelectedAdf = new Intent();
        returnSelectedAdf.putExtra("ADF", uuid);
        setResult(RESULT_OK, returnSelectedAdf);
        finish();
    }
}



