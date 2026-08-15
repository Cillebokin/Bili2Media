package androidx.documentfile.provider;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public final class FakeDocumentFile extends DocumentFile {
    private final Uri uri;
    private final String name;
    private final boolean directory;
    private final boolean readable;
    private final long byteLength;
    private final List<DocumentFile> children = new ArrayList<>();

    private FakeDocumentFile(
            String id,
            String name,
            boolean directory,
            boolean readable,
            long byteLength
    ) {
        super(null);
        this.uri = Uri.parse("content://bili2media.test/" + id);
        this.name = name;
        this.directory = directory;
        this.readable = readable;
        this.byteLength = byteLength;
    }

    public static FakeDocumentFile directory(String id, String name) {
        return new FakeDocumentFile(id, name, true, true, 0L);
    }

    public static FakeDocumentFile unreadableDirectory(String id, String name) {
        return new FakeDocumentFile(id, name, true, false, 0L);
    }

    public static FakeDocumentFile file(String id, String name, long byteLength) {
        return new FakeDocumentFile(id, name, false, true, byteLength);
    }

    public FakeDocumentFile add(DocumentFile child) {
        children.add(child);
        return this;
    }

    @Override
    public DocumentFile createFile(String mimeType, String displayName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentFile createDirectory(String displayName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return directory ? null : "application/octet-stream";
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isFile() {
        return !directory;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public long lastModified() {
        return 0L;
    }

    @Override
    public long length() {
        return byteLength;
    }

    @Override
    public boolean canRead() {
        return readable;
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public DocumentFile[] listFiles() {
        return children.toArray(new DocumentFile[0]);
    }

    @Override
    public boolean renameTo(String displayName) {
        return false;
    }
}
