package com.movel.protetivovitima;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.movel.protetivovitima.events.EventsLatLong;
import com.movel.protetivovitima.events.ExternalLatLong;
import com.movel.protetivovitima.events.InternalLatLong;
import com.movel.protetivovitima.utils.DiffDistance.*;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.ConnectionFactory;
import br.ufma.lsdi.cddl.listeners.IConnectionListener;
import br.ufma.lsdi.cddl.listeners.ISubscriberListener;
import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.cddl.network.ConnectionImpl;
import br.ufma.lsdi.cddl.network.SecurityService;
import br.ufma.lsdi.cddl.pubsub.Publisher;
import br.ufma.lsdi.cddl.pubsub.PublisherFactory;
import br.ufma.lsdi.cddl.pubsub.Subscriber;
import br.ufma.lsdi.cddl.pubsub.SubscriberFactory;

public class MainActivity extends AppCompatActivity {

    EPServiceProvider engine;
    CDDL cddl;

    private EditText editTextView;
    private TextView messageTextView;
    private TextView sosText;
    private View subscribeButton;
    private View sosButton;
    private ConnectionImpl con;
    private ConnectionImpl external_con;
    private boolean clickedButton = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setViews();

        if(!checkPermission()){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        startCEP();
        subscribeButton.setOnClickListener(clickListener);
    }

    private void setViews() {
        editTextView = findViewById(R.id.editText);
        subscribeButton = findViewById(R.id.subscribeButton);
        sosButton = findViewById(R.id.sosButton);
        messageTextView = findViewById(R.id.messageTexView);
        sosText = findViewById(R.id.sosText);
    }

    private void initCDDL(String clientId) {
        String host = CDDL.startMicroBroker();
        con = ConnectionFactory.createConnection();
        con.setClientId(clientId);
        con.setHost(host);
        con.connect();

        String host_external = "dev.correia.xyz";
        external_con = ConnectionFactory.createConnection();
        external_con.setClientId(clientId);
        external_con.setHost(host_external);
        external_con.addConnectionListener(connectionListenerExternal);
        external_con.connect();
        cddl = CDDL.getInstance();
        cddl.setConnection(con);
        cddl.setContext(this);
        cddl.startService();
        cddl.startLocationSensor();
        cddl.startCommunicationTechnology(CDDL.INTERNAL_TECHNOLOGY_ID);
    }

    private void publishExternal(Double lat, Double lon) {
        Publisher publisher = PublisherFactory.createPublisher();
        publisher.addConnection(external_con);
        MyMessage message = new MyMessage();
        message.setServiceName("location_external");
        message.setServiceValue(lat + ";" + lon);
        publisher.publish(message);
    }

    // Subscribe into External Broker
    private void subscribeExternalMessage(String clientId) {
        Subscriber sub = SubscriberFactory.createSubscriber();
        sub.addConnection(external_con);
        sub.subscribeServiceByPublisherAndName(clientId,"location_external");
        sub.subscribeServiceByName("alert");
        sub.subscribeServiceByName("my_service");
        sub.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("location_external")) {
                    String[] arrOfPoints = message.getServiceValue()[0].toString().split(";");
                    double external_latitude = Double.parseDouble(arrOfPoints[0]);
                    double external_longitude = Double.parseDouble(arrOfPoints[1]);
//                    ExternalBroke.setLocations(external_latitude, external_longitude);
                    engine.getEPRuntime().sendEvent(new ExternalLatLong(external_latitude, external_longitude));

                }
                Log.d("_MAIN_EXTERNAL", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>" + message);
            }
        });
    }

    // Subscribe into Local Broker
    private void subscribeMessage() {
        Subscriber sub = SubscriberFactory.createSubscriber();
        sub.addConnection(cddl.getConnection());
        sub.subscribeServiceByName("Location");
        sub.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("Location")) {
                    double latitude = message.getSourceLocationLatitude();
                    double longitude = message.getSourceLocationLongitude();
//                    LocalBroke.setLocations(latitude, longitude);
                    engine.getEPRuntime().sendEvent(new InternalLatLong(latitude, longitude));
                }
            }
        });
    }

    private void startCEP() {
        engine = EPServiceProviderManager.getDefaultProvider();
        engine.getEPAdministrator().getConfiguration().addEventType(InternalLatLong.class);
        engine.getEPAdministrator().getConfiguration().addEventType(ExternalLatLong.class);

        String getLastEvents = "insert into LocationUpdate select * from InternalLatLong.std:lastevent() as internal, ExternalLatLong.std:lastevent() as external";
        engine.getEPAdministrator().createEPL(getLastEvents);

        String calculateDistance = "insert into Distancia select com.movel.protetivovitima.utils.DiffDistance.difference(internal, external) as distancia from LocationUpdate";
        engine.getEPAdministrator().createEPL(calculateDistance);

        String selectDistance = "select * from Distancia where distancia < 1";
        EPStatement statement4 = engine.getEPAdministrator().createEPL(selectDistance);

        statement4.addListener(new UpdateListener() {
            @Override
            public void update(EventBean[] newData, EventBean[] oldData) {
                Log.d(">>CEP:", newData[0].get("distancia").toString());
                int visibility = sosButton.getVisibility();
                Log.d(">>CEP Visibility:", Integer.toString(visibility));
                sosText.setText(R.string.SOS);
                if(visibility != View.VISIBLE) {
                    sosButton.setVisibility(View.VISIBLE);
                }
            }
        });

        String distanceAbove = "select * from Distancia where distancia > 1";
        EPStatement statement5 = engine.getEPAdministrator().createEPL(distanceAbove);

        statement5.addListener(new UpdateListener() {
            @Override
            public void update(EventBean[] newData, EventBean[] oldData) {
                int visibility = sosButton.getVisibility();
                sosText.setText("");
                if(visibility == View.VISIBLE) {
                    sosButton.setVisibility(View.GONE);
                }
            }
        });
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String et = editTextView.getText().toString();
            initCDDL(et);
            subscribeMessage();
            subscribeExternalMessage(et);
        }
    };

    private IConnectionListener connectionListenerExternal = new IConnectionListener() {
        @Override
        public void onConnectionEstablished() {
            messageTextView.setText("Conexão estabelecida.");
        }

        @Override
        public void onConnectionEstablishmentFailed() {
            messageTextView.setText("Falha na conexão.");
        }

        @Override
        public void onConnectionLost() {
            messageTextView.setText("Conexão perdida.");
        }

        @Override
        public void onDisconnectedNormally() {
            messageTextView.setText("Uma disconexão normal ocorreu.");
        }

    };

    @Override
    protected void onDestroy() {
        cddl.stopLocationSensor();
        cddl.stopAllCommunicationTechnologies();
        cddl.stopService();
        con.disconnect();
        CDDL.stopMicroBroker();
        super.onDestroy();
    }

    private boolean checkPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int result = ContextCompat.checkSelfPermission(getApplicationContext(), READ_EXTERNAL_STORAGE);
            int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
//                intent.putExtra(caFileName, caFileName);
//                intent.putExtra(certFileName, certFileName);
//                intent.putExtra(caAlias, caAlias);
                startActivityForResult(intent, 2296);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 2296);
            }
        } else {
            //below android 11
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE}, 1);
        }
    }

    private void setCertificates(String caFileName, String certFileName, String caAlias) {
        if (!checkPermission()) {
            requestPermission();
        } else {
            try {
                SecurityService securityService = new SecurityService(getApplicationContext());
                securityService.setCaCertificate(caFileName, caAlias);
                securityService.setCertificate(certFileName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2296) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "All permissions are granted, you may import certificates!", Toast.LENGTH_SHORT).show();

//                    try{
//                        SecurityService securityService = new SecurityService(getApplicationContext());
//                        String caFileName = data.getStringExtra("caFileName");
//                        String certFileName = data.getStringExtra("certFileName");
//                        String caAlias = data.getStringExtra("caAlias");
//
//                        securityService.setCaCertificate(caFileName, caAlias);
//                        securityService.setCertificate(certFileName);
//                    }
//                    catch (Exception e){
//                        e.printStackTrace();
//                    }
                } else {
                    Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}