package it.pgp.xfiles.dialogs;

import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;

import java.io.IOException;
import java.security.PublicKey;

import it.pgp.xfiles.MainActivity;
import it.pgp.xfiles.R;
import it.pgp.xfiles.sftpclient.AuthData;
import it.pgp.xfiles.sftpclient.SFTPProvider;
import it.pgp.xfiles.utils.pathcontent.BasePathContent;

/**
 * Created by pgp on 03/03/17
 *
 * Dialog to be displayed when a host key does not match the one
 * already in known hosts. Choices are:
 *    - overwrite old host key and continue connecting
 *    - keep old host key and abort connection
 *    - (not really necessary: temporarily accept new host key, but don't remove old one)
 */

public class SSHAlreadyInKnownHostsDialog extends SSHKnownHostsBaseDialog {

    public SSHAlreadyInKnownHostsDialog(final MainActivity activity,
                                        final AuthData authData,
                                        final OpenSSHKnownHosts.KnownHostEntry oldHostEntry,
                                        final PublicKey newHostKey,
                                        final SFTPProvider provider,
                                        final BasePathContent pendingLsPath) {
        super(activity,pendingLsPath);

        setTitle("Conflicting host key");
        setContentView(R.layout.ssh_already_in_known_hosts_dialog);
        TextView oldFingerprint = findViewById(R.id.storedHostKeyFingerprintTextView);
        TextView newFingerprint = findViewById(R.id.currentHostKeyFingerprintTextView);
        Button accept = findViewById(R.id.hostKeyAcceptOverwriteButton);
        Button discard = findViewById(R.id.hostKeyKeepOldAndDisconnectButton);

        if(oldHostEntry != null)
            oldFingerprint.setText(oldHostEntry.getType().name()+"\n"+oldHostEntry.getFingerprint());

        newFingerprint.setText(KeyType.fromKey(newHostKey)+" "+newHostKey.getAlgorithm()+" "+newHostKey.getFormat()+"\n"+getHostkeyFingerprint(newHostKey));

        accept.setOnClickListener(v -> {
            try {
                final String adjustedHostname = (authData.port != 22) ? "[" + authData.domain + "]:" + authData.port : authData.domain;
                provider.updateHostKey(adjustedHostname,newHostKey);
                Toast.makeText(activity,"Host key updated in known hosts",Toast.LENGTH_LONG).show();
                dismiss();
                // retry getChannel and LS pending request (if any) is done in dismiss listener
            } catch (IOException e) {
                Toast.makeText(activity,"Unable to update host key in known hosts",Toast.LENGTH_LONG).show();
                resetPath();
                cancel();
            }
        });

        discard.setOnClickListener(v -> {
            resetPath();
            cancel();
        });
    }
}
