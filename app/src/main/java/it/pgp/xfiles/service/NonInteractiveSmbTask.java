package it.pgp.xfiles.service;

import android.widget.Toast;

import java.io.Serializable;

import it.pgp.xfiles.MainActivity;
import it.pgp.xfiles.enums.FileOpsErrorCodes;
import it.pgp.xfiles.enums.ProviderType;
import it.pgp.xfiles.roothelperclient.ControlCodes;
import it.pgp.xfiles.service.params.CopyMoveParams;
import it.pgp.xfiles.sftpclient.XProgress;
import it.pgp.xfiles.utils.pathcontent.BasePathContent;

public class NonInteractiveSmbTask extends BaseBackgroundTask {
    /*
     * determine operation type (DOWNLOAD or UPLOAD) by type inspection of CopyMoveParams:
     * - DOWNLOAD:
     *      CopyMoveParams.CopyMoveListPathContent.BasePathContent (parentDir) -> SMBPathContent
     *      CopyMoveParams.BasePathContent -> LocalPathContent
     * - UPLOAD:
     *      CopyMoveParams.CopyMoveListPathContent.BasePathContent (parentDir) -> LocalPathContent
     *      CopyMoveParams.BasePathContent -> SMBPathContent
     */
    public CopyMoveParams params;
    public ControlCodes action;

    private BasePathContent currentDir; // for refreshing dir listview (if not changed meanwhile) on operation end
    Exception lastException;

    public NonInteractiveSmbTask(Serializable params_) {
        super(params_);
        params = (CopyMoveParams) params_;

        if (params.list.parentDir.providerType == ProviderType.SMB &&
                params.destPath.providerType == ProviderType.LOCAL)
            action = ControlCodes.ACTION_DOWNLOAD;
        else if (params.list.parentDir.providerType == ProviderType.LOCAL &&
                params.destPath.providerType == ProviderType.SMB)
            action = ControlCodes.ACTION_UPLOAD;
        else throw new RuntimeException("Unexpected CopyMoveParams content");
    }

    @Override
    public boolean init(BaseBackgroundService service) {
        if (!super.init(service)) return false;
        mr = new XProgress(service, wm);
        return true;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        currentDir = MainActivity.mainActivity.getCurrentDirCommander().getCurrentDirectoryPathname();
    }

    @Override
    protected Object doInBackground(Object[] unusedParams) {
        try {
            MainActivity.smbProvider.initProgressSupport(this);
            MainActivity.smbProvider.copyMoveFilesToDirectory(
                    this.params.list,this.params.destPath);
        }
        catch (Exception e) {
            e.printStackTrace();
            lastException = e;
            result = FileOpsErrorCodes.TRANSFER_ERROR;
        }
        return result;
    }

    // TODO implement if needed
//    @Override
//    public void cancelTask() {
//         super.cancelTask();
//    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);

        if (result == null || result == FileOpsErrorCodes.OK) {
            // refresh dir only if it's the same of when the task started
            Toast.makeText(service,"Remote transfer completed",Toast.LENGTH_LONG).show();
            MainActivity activity = MainActivity.mainActivity;
            if (activity == null) return; // activity closed while service active, nothing to refresh
            BasePathContent cd = activity.getCurrentDirCommander().getCurrentDirectoryPathname();
            if (cd.equals(currentDir))
                activity.browserPagerAdapter.showDirContent(activity.getCurrentDirCommander().refresh(),activity.browserPager.getCurrentItem(),params.list.files.get(0).getFilename());
        }
        else if (result == FileOpsErrorCodes.TRANSFER_CANCELLED) {
            Toast.makeText(service,params.list.copyOrMove.name().toLowerCase()+" cancelled",Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(service,params.list.copyOrMove.name().toLowerCase()+" error: "+result.getValue()+
                    "\nReason: "+(lastException==null?"null":lastException.getMessage()),Toast.LENGTH_LONG).show();
        }
    }
}
