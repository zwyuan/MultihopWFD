package edu.ucla.cs.zyuan.multihopwfd;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;
import android.widget.TextView;

public class DebugPanel extends ScrollView {

    private StringBuilder sb=new StringBuilder();
    private TextView tv;
    public DebugPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        tv = new TextView(context);
        tv.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(tv);
    }

    public void appendNewLine(String s)
    {
        sb.append(s);
        sb.append("\n");
        tv.setText(sb.toString());
        fullScroll(FOCUS_DOWN);
    }

}
