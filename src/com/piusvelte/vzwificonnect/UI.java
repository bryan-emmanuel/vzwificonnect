package com.piusvelte.vzwificonnect;

import java.util.List;

import com.admob.android.ads.AdListener;
import com.admob.android.ads.AdView;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class UI extends ListActivity implements AdListener, View.OnClickListener, CompoundButton.OnCheckedChangeListener, DialogInterface.OnClickListener {
	public static final int RESCAN_ID = Menu.FIRST;
	public static final int WIFI_ID = Menu.FIRST + 1;
	public static final int CONNECT_ID = Menu.FIRST + 2;
	private Button btn_generator;
	private EditText fld_ssid, fld_wep, fld_wep_alternate;
	private CheckBox btn_wifi;
	private WifiManager mWifiManager;
	private Context mContext;
	private String[] mSsid = new String[0], mBssid = new String[0];
	private boolean wifiEnabled;
	public static final String TAG = "VzWiFiConnect";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		registerForContextMenu(getListView());
		mContext = this;
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		fld_ssid = (EditText) findViewById(R.id.fld_ssid);
		fld_wep = (EditText) findViewById(R.id.fld_wep);
		fld_wep_alternate = (EditText) findViewById(R.id.fld_wep_alternate);
		btn_generator = (Button) findViewById(R.id.btn_generator);
		btn_wifi = (CheckBox) findViewById(R.id.btn_wifi);
		btn_generator.setOnClickListener(this);
		btn_wifi.setOnCheckedChangeListener(this);
		wifiStateChanged(mWifiManager.getWifiState());
		mWifiManager.startScan();
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setMessage(R.string.usage);
		dialog.setNegativeButton(R.string.close, this);
		dialog.show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, RESCAN_ID, 0, R.string.rescan).setIcon(android.R.drawable.ic_menu_search);
		menu.add(0, WIFI_ID, 0, R.string.wifi_settings).setIcon(android.R.drawable.ic_menu_manage);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case RESCAN_ID:
			return true;
		case WIFI_ID:
			startActivity(new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.wifi.WifiSettings")));
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		menu.add(0, CONNECT_ID, 0, R.string.connect);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getItemId() == CONNECT_ID) {
			int pos = ((AdapterContextMenuInfo) item.getMenuInfo()).position;
			String ssid = mSsid[pos], bssid = mBssid[pos];
			final List<WifiConfiguration> lwc = mWifiManager.getConfiguredNetworks();
			int networkId = -1;
			for (int i = lwc.size() - 1; i >= 0; i--) {
				final WifiConfiguration wifiConfig = lwc.get(i);
				// compare ssid & bssid
				if (wifiConfig.BSSID.equals(ssid) && wifiConfig.SSID.equals(bssid)) {
					networkId = wifiConfig.networkId;
					wifiConfig.wepKeys[0] = bssid + generator();
					mWifiManager.updateNetwork(wifiConfig);
					break;
				}
			}
			if (networkId == -1) {
				WifiConfiguration wc = new WifiConfiguration();
				wc.SSID = ssid;
				wc.BSSID = bssid;
				wc.wepKeys[0] = bssid + generator();
				networkId = mWifiManager.addNetwork(wc);
			}
			// disable others to force connection to this network
			mWifiManager.enableNetwork(networkId, true);
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id) {
		super.onListItemClick(list, view, position, id);
		fld_ssid.setText(mSsid[position]);
		fld_wep.setText(mBssid[position].substring(3,5).toUpperCase() + mBssid[position].substring(6,8).toUpperCase() + generator());
		fld_wep_alternate.setText("");
	}
	
	public String generator() {
		int dec = 0;
		String ssid = fld_ssid.getText().toString().toUpperCase();
		for (int i = 0; i < ssid.length(); i++) {
			try {
				dec += Integer.parseInt(Character.toString(ssid.charAt(i))) * Math.pow(36, i);
			} catch (NumberFormatException nfe) {
				dec += Character.toString(ssid.charAt(i)).charAt(0) - 55;				
			}
		}
		String wep = "";
		if (6 > toHex(dec).length()) for (int i = 0; i < (6 - toHex(dec).length()); i++) wep += 0;
		else wep += toHex(dec);
		return wep;
	}

	public String toHex(int dec) {
		int rem = dec % 16;
		String hex = "0123456789ABCDEF";
		if (dec - rem == 0) return (rem > 16 ? "" : Character.toString(hex.charAt(rem)));
		else return (rem > 16 ? "" : toHex((dec - rem) / 16) + Character.toString(hex.charAt(rem)));
	}
	
	public void btnWifiSetText(String msg) {
		btn_wifi.setText(getString(R.string.wifi) + " " + msg);		
	}
	
	public void wifiStateChanged(int state) {
		switch (state) {
		case WifiManager.WIFI_STATE_ENABLING:
			btnWifiSetText(getString(R.string.enabling));
			return;
		case WifiManager.WIFI_STATE_ENABLED:
			btnWifiSetText(getString(R.string.enabled));
			wifiEnabled = true;
			btn_wifi.setChecked(true);
			return;
		case WifiManager.WIFI_STATE_DISABLING:
			btnWifiSetText(getString(R.string.disabling));
			wifiEnabled = false;
			btn_wifi.setChecked(false);
			return;
		case WifiManager.WIFI_STATE_DISABLED:
			btnWifiSetText(getString(R.string.disabled));
			return;
		}
	}
	
	public void networkStateChanged(NetworkInfo ni) {
		if (ni.isConnected()) btnWifiSetText(getString(R.string.connected));
		else if (ni.isConnectedOrConnecting()) btnWifiSetText(getString(R.string.connecting));
		else wifiStateChanged(mWifiManager.getWifiState());
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
				List<ScanResult> lsr = mWifiManager.getScanResults();
				mSsid = new String[0];
				mBssid = new String[0];
				for (ScanResult sr : lsr) {
					String bssid = sr.BSSID.substring(3,5).toUpperCase() + sr.BSSID.substring(6,8).toUpperCase();
					if ((sr.SSID.length() == 5) && (bssid.equals("1801") || bssid.equals("1F90"))) {
						String[] ssid_cp = new String[mSsid.length];
						String[] bssid_cp = new String[mSsid.length];
						for (int i = 0; i < mSsid.length; i++) {
							ssid_cp[i] = mSsid[i];
							bssid_cp[i] = mBssid[i];
						}
						mSsid = new String[ssid_cp.length + 1];
						mBssid = new String[ssid_cp.length + 1];
						for (int i = 0; i < mSsid.length; i++) {
							if (i == (mSsid.length - 1)) {
								mSsid[i] = sr.SSID;
								mBssid[i] = sr.BSSID;
							} else {
								mSsid[i] = ssid_cp[i];
								mBssid[i] = bssid_cp[i];
							}
						}
					}
				}
				setListAdapter(new ArrayAdapter<String>(mContext, android.R.layout.simple_list_item_1, mSsid));
			} else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) networkStateChanged((NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO));
			else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) wifiStateChanged(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));
		}
	};

	@Override
	public void onResume() {
		super.onResume();
		IntentFilter f = new IntentFilter();
		f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(mReceiver, f);
		setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mSsid));
	}

	@Override
	public void onPause() {
		super.onDestroy();
		if (mReceiver != null) {
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
	}

	@Override
	public void onFailedToReceiveAd(AdView arg0) {}

	@Override
	public void onFailedToReceiveRefreshedAd(AdView arg0) {}

	@Override
	public void onReceiveAd(AdView arg0) {}

	@Override
	public void onReceiveRefreshedAd(AdView arg0) {}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (isChecked != wifiEnabled) mWifiManager.setWifiEnabled(isChecked);
	}

	@Override
	public void onClick(View v) {
		fld_wep.setText("1801" + generator());
		fld_wep_alternate.setText("1F90" + generator());
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		dialog.cancel();
	}
}