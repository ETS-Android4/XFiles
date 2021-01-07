package it.pgp.xfiles.roothelperclient;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import it.pgp.xfiles.BrowserItem;
import it.pgp.xfiles.FindActivity;
import it.pgp.xfiles.MainActivity;
import it.pgp.xfiles.adapters.FindResultsAdapter;
import it.pgp.xfiles.roothelperclient.reqs.find_rq;
import it.pgp.xfiles.roothelperclient.resps.find_resp;
import it.pgp.xfiles.service.SocketNames;
import it.pgp.xfiles.utils.Misc;

/**
 * Created by pgp on 22/01/18
 * Updater class for find tasks, to be embedded into AsyncTask/Service if needed
 */

public class FindManager implements AutoCloseable {
    private static final SocketNames defaultaddress = SocketNames.theroothelper;

    // common access instances
    public static final AtomicReference<Thread> findManagerThreadRef = new AtomicReference<>(null);

    // streams connected to local socket
    protected DataInputStream i;
    protected OutputStream o;

    // BEGIN common code with RemoteManager
    private FindManager() throws IOException {
        LocalSocket clientSocket = new LocalSocket();
        LocalSocketAddress socketAddress = new LocalSocketAddress(
                defaultaddress.name(),
                LocalSocketAddress.Namespace.ABSTRACT);
        clientSocket.connect(socketAddress);
        Log.d(getClass().getName(),"Connected");

        o = clientSocket.getOutputStream();
        i = new DataInputStream(clientSocket.getInputStream());
        Log.d(getClass().getName(),"Streams acquired");
    }

    @Override
    public void close() {
        try {i.close();} catch (Exception ignored) {}
        try {o.close();} catch (Exception ignored) {}
        Log.d(getClass().getName(),"Streams closed");
    }
    // END common code with RemoteManager

    private boolean start_find(find_rq find_rq) throws IOException {
        // start RH find thread
        find_rq.write(o);

        int resp = Misc.receiveBaseResponse(i);
        if (resp != 0) {
            // Unable to start RH find thread
            return false;
        }
        // ok, RH find thread started
        // now, start rhss update thread
        new FindUpdatesThread(find_rq).start();
        return true;
    }

    // cancel current search, if any
    private boolean stop_find() throws IOException {
        new find_rq().write(o);

        int resp = Misc.receiveBaseResponse(i);
        if (resp != 0) return false;
        // ok, RH find thread stopped, local updates thread will end automatically as well
        findManagerThreadRef.set(null);
        return true;
    }

    ////////////////////////////////////
    // methods with auto-close after request send

    public enum FIND_ACTION {START,STOP}

    public static int find_action(FIND_ACTION action, find_rq... request) {
        if (action == FIND_ACTION.START) {
            MainActivity.getRootHelperClient();
            // without auto-close
            try {return (new FindManager().start_find(request[0])) ? 1 : 0;}
            catch (IOException e) {return -1;}
        }
        else {
            // with auto-close
            try(FindManager f = new FindManager()) {
                return f.stop_find()?1:0;
            }
            catch (IOException ignored) {}
            return -1;
        }
    }

    ////////////////////////////////////

    // callback (stub)
    private boolean onSearchItemFound(find_resp item) {
        try {
            // TODO when content search will be available, should replace BrowserItem with a subclass including content results
            FindActivity.instance.runOnUiThread(() ->
                    FindResultsAdapter.instance.add(
                            new BrowserItem(item.fileItem)));
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private class FindUpdatesThread extends Thread {
        private final find_rq rq;
        FindUpdatesThread(find_rq rq) {
            this.rq = rq;
        }

        @Override
        public void run() {
            try {
                // strong cas, a thread is guaranteed to win
                if (!findManagerThreadRef.compareAndSet(null,this)) {
                    MainActivity.showToastOnUI("Another find thread is already receiving updates");
                    return;
                }

                FindActivity.instance.runOnUiThread(()->FindActivity.instance.toggleSearchButtons(true));

                // create new adapter
                FindResultsAdapter.createAdapter(rq);

                for(;;) { // exits on IOException when the other socket endpoint is closed (search interrupted), or when receives end of list (not strictly needed, roothelper find thread could also close the connection after sending last item found)

                    // receive search results
                    find_resp item = find_resp.readNext(i);
                    if (item == null) break;
                    if (!onSearchItemFound(item)) break; // exit immediately if adapter has been destroyed (actually, that should not happen)
                }
                MainActivity.showToastOnUI("Search completed");
            }
            catch (Throwable t) {
                t.printStackTrace();
                Log.d(getClass().getName(),"Local socket closed by rhss server or other exception, exiting...");
            }
            finally {
                close();
            }
            findManagerThreadRef.set(null); // unset reference only if compareAndSet was successful
            FindActivity.instance.runOnUiThread(()->FindActivity.instance.toggleSearchButtons(false));
            Log.d(getClass().getName(),"Really exiting find update thread now!");
        }
    }
}
