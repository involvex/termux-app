package com.invapp.app.api.file;

import org.junit.Assert;
import org.junit.Test;

public class FileReceiverAttachmentNameTest {

    @Test
    public void testIsSafeAttachmentFileName() {
        Assert.assertTrue(FileReceiverActivity.isSafeAttachmentFileName("photo.jpg"));
        Assert.assertTrue(FileReceiverActivity.isSafeAttachmentFileName("my file.txt"));

        Assert.assertFalse(FileReceiverActivity.isSafeAttachmentFileName(null));
        Assert.assertFalse(FileReceiverActivity.isSafeAttachmentFileName(""));
        Assert.assertFalse(FileReceiverActivity.isSafeAttachmentFileName("../etc/passwd"));
        Assert.assertFalse(FileReceiverActivity.isSafeAttachmentFileName("foo/bar.txt"));
        Assert.assertFalse(FileReceiverActivity.isSafeAttachmentFileName("foo\\bar.txt"));
        Assert.assertFalse(FileReceiverActivity.isSafeAttachmentFileName(".../...//passwd"));
    }
}
