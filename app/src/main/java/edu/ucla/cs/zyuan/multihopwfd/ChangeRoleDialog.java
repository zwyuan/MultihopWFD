package edu.ucla.cs.zyuan.multihopwfd;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler.AnonymousPacketHandler;

public class ChangeRoleDialog extends DialogFragment {
    public int mSelected = -1;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CharSequence[] items = { "Normal", "Anonym. Server Type 1", "Anonym. Server Type 2", "Anonym. Server Test" };
        mSelected = getArguments().getInt("currentRole");
        switch (mSelected) {
            case AnonymousPacketHandler.ROLE_NORMAL:
                mSelected = 0;
                break;
            case AnonymousPacketHandler.ROLE_ANONYM_SERVER_1:
                mSelected = 1;
                break;
            case AnonymousPacketHandler.ROLE_ANONYM_SERVER_2:
                mSelected = 2;
                break;
            case AnonymousPacketHandler.ROLE_TEST:
                mSelected = 3;
                break;
            default:
                mSelected = -1;
                break;
        }

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Choose Your Role")
                .setSingleChoiceItems(items, mSelected,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mSelected = which;
                            }
                        })
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ((WiFiDirectActivity) getActivity()).changeRole(mSelected);
                    }
                });

        // Create the AlertDialog object and return it
        return builder.create();
    }
}