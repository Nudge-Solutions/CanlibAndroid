package com.kvaser.canlibdemo;

import android.os.*;
import android.support.v4.app.*;
import android.view.*;
import android.widget.*;
import com.kvaser.canlib.*;

import java.util.*;

/**
 * Shows a text view which scrolls down when text is appended.
 */
public class PrintoutFragment extends Fragment {

  private static final int MAX_LINES = 1000;
  private TextView textView;
  private ScrollView scrollView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_printout, container, false);
    textView = (TextView) view.findViewById(R.id.text_id_output);
    textView.setText("");
    scrollView = (ScrollView) view.findViewById(R.id.scroll_id);
    scrollDown();
    return view;
  }
  
  /**
   * Appends the supplied string to the text view and scrolls down.
   *
   * @param string The string to append.
   */
  public void appendText(String string) {
    textView.append(string);
    scrollDown();
  }
  
  private void scrollDown() {
    scrollView.post(new Runnable() {
      public void run() {
        scrollView.fullScroll(View.FOCUS_DOWN);
      }
    });
  }

  synchronized public void appendMessage(CanMessage msg) {
    if (!msg.isFlagSet(CanMessage.MessageFlags.ERROR_FRAME)) {
      String data = "";
      int dataLen = Math.min(msg.dlc, 8);
      for (int j = 0; j < dataLen; j++) {
        if ((((int) msg.data[j]) & 0xff) < 0x10) {
          data = data + "0";
        }
        data = data + Integer.toHexString(((int) msg.data[j]) & 0xff).toUpperCase() + " ";
      }

      textView.append(
          msg.getDirection() + ": ID: " + Long.toHexString(msg.id).toUpperCase() + " Data: "
          + data + "\n");
    } else {
      textView.append(msg.getDirection() + ": ID: 0 Data: Error frame\n");
    }

    // Erase excessive lines
    int excessLineNumber = textView.getLineCount() - MAX_LINES;
    if (excessLineNumber > 0) {
      int eolIndex = -1;
      CharSequence charSequence = textView.getText();
      for (int i = 0; i < excessLineNumber; i++) {
        do {
          eolIndex++;
        } while (eolIndex < charSequence.length() && charSequence.charAt(eolIndex) != '\n');
      }
      if (eolIndex < charSequence.length()) {
        textView.getEditableText().delete(0, eolIndex + 1);
      } else {
        textView.setText("");
      }
    }
    scrollDown();
  }

  synchronized public void addMessages(CanMessage[] list) {
    if (textView != null) {
      textView.setText("");
      if (list != null) {
        int startIndex = 0;
        if (list.length > MAX_LINES) {
          startIndex = list.length - MAX_LINES;
        }
        for (int i = startIndex; i < list.length; i++) {
          CanMessage msg = list[i];
          if (!msg.isFlagSet(CanMessage.MessageFlags.ERROR_FRAME)) {
            String data = "";
            int dataLen = Math.min(msg.dlc, 8);
            for (int j = 0; j < dataLen; j++) {
              if ((((int) msg.data[j]) & 0xff) < 0x10) {
                data = data + "0";
              }
              data = data + Integer.toHexString(((int) msg.data[j]) & 0xff).toUpperCase() + " ";
            }

            textView.append(
                msg.getDirection() + ": ID: " + Long.toHexString(msg.id).toUpperCase() + " Data: "
                + data + "\n");
          } else {
            textView.append(msg.getDirection() + ": ID: 0 Data: Error frame\n");
          }
        }
      }
      scrollDown();
    }
  }
}

