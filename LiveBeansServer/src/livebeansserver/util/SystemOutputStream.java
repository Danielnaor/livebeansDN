
package livebeansserver.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import livebeanscommon.ISysOutWatcher;


public class SystemOutputStream extends FilterOutputStream
{

    private final ArrayList<ISysOutWatcher> _watchers;

    public SystemOutputStream(OutputStream out)
    {
        super(out);

        _watchers = new ArrayList<>();
    }

    public void addWatcher(ISysOutWatcher newWatcher)
    {
        _watchers.add(newWatcher);
    }

    @Override
    public void write(byte b[]) throws IOException
    {
        updateWatchers(new String(b));
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException
    {
        updateWatchers(new String(b, off, len));
    }

    private void updateWatchers(String updatedString)
    {
        if (_watchers.isEmpty())
        {
            return;
        }

        _watchers.stream().forEach((watcher)
                ->
                {
                    watcher.onPrintLine(updatedString);
        });
    }

}
