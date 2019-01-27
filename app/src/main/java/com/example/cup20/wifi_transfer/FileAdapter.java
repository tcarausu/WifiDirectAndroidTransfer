package com.example.cup20.wifi_transfer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by cup20 on 2016/11/7.
 */

public class FileAdapter extends BaseAdapter {
    private LayoutInflater inflater;
    private ArrayList<String> names = null;
    private ArrayList<String> paths = null;

    public FileAdapter(Context context, ArrayList<String> na, ArrayList<String> pa) {
        names = na;
        paths = pa;
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return names.size();
    }

    @Override
    public Object getItem(int position) {
        // TODO Auto-generated method stub
        return names.get(position);
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View listItemView = convertView;
        if (null == convertView) {
            listItemView = inflater.inflate(R.layout.my_list, parent, false);
        }
        File f = new File(paths.get(position).toString());
        TextView textView = (TextView) listItemView.findViewById(R.id.fileName);
        ImageView image = (ImageView) listItemView.findViewById(R.id.ICON);
        if (names.get(position).equals("@1")) {
            textView.setText("/");
            image.setImageResource(R.drawable.folder);
        } else if (names.get(position).equals("@2")) {
            textView.setText("..");
            image.setImageResource(R.drawable.folder);
        } else {
            textView.setText(f.getName());
            if (f.isDirectory()) {
                image.setImageResource(R.drawable.folder);
            } else if (f.isFile()) {
                image.setImageResource(R.drawable.file);
            }
        }
        return listItemView;
    }

}
