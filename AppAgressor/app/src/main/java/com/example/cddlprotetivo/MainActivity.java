package com.example.cddlprotetivo;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.annotation.SuppressLint;
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

    CDDL cddl;
    private TextView messageTextView;
    private EditText editTextView;
    private View sendButton;
    private ConnectionImpl con;
    private ConnectionImpl external_con;
    private Double latitude = 0.0;
    private Double longitude = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setViews();

        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        sendButton.setOnClickListener(clickListener);
    }

    private void setViews() {
        editTextView = findViewById(R.id.editText);
        messageTextView = findViewById(R.id.messageTexView);
        sendButton = findViewById(R.id.sendButton);
    }

    private void initCDDL(String clientId) {
        String host = CDDL.startMicroBroker();
        con = ConnectionFactory.createConnection();
        con.setClientId(clientId); //criado manualmente ou automaticamente?
        con.setHost(host);
        con.addConnectionListener(connectionListener);
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

    private void subscribeExternalMessage(String clientId) {
        Subscriber sub = SubscriberFactory.createSubscriber();
        sub.addConnection(external_con);
        sub.subscribeServiceByName("location_external");
        sub.subscribeServiceByName("alert");
        sub.subscribeServiceByName("my_service");
        sub.setSubscriberListener(new ISubscriberListener() {
            @SuppressLint("LongLogTag")
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("my_service")) {
                    Log.d("_MAIN_EXTERNAL_MY_SERVICE", "+++" + message.toString());
                }
                if (message.getServiceName().equals("alert")) {
                    Log.d("_MAIN_EXTERNAL_ALERTA", "+++" + message.toString());
                }
                if (message.getServiceName().equals("location_external")) {
                    Log.d("_MAIN_EXTERNAL_LOCATION", "+++" + message.toString());
                }
                Log.d("_MAIN_EXTERNAL", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>" + message);
            }
        });
    }

    private void subscribeMessage() {
        Subscriber sub = SubscriberFactory.createSubscriber();
        sub.addConnection(cddl.getConnection());
        sub.subscribeServiceByName("Location");
        sub.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("Location")) {
                    latitude = message.getSourceLocationLatitude();
                    longitude = message.getSourceLocationLongitude();
                }
                Log.d("_MAIN", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>" + message);
            }
        });
    }

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

    private IConnectionListener connectionListener = new IConnectionListener() {
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

    private View.OnClickListener sendListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            publishExternal(latitude, longitude);
        }
    };

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String et = editTextView.getText().toString();
            initCDDL(et);
            subscribeMessage();
            subscribeExternalMessage(et);
            thread.start();
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

    Thread thread = new Thread() {
        @Override
        public void run() {
            try {
                while(true) {
                    sleep(1000);
                    publishExternal(latitude, longitude);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

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
                } else {
                    Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}