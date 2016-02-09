/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package alluxio.client;

import alluxio.AlluxioURI;
import alluxio.Configuration;
import alluxio.LocalAlluxioClusterResource;
import alluxio.client.file.FileInStream;
import alluxio.client.file.FileSystem;
import alluxio.client.file.options.CreateFileOptions;
import alluxio.exception.AlluxioException;
import alluxio.util.io.BufferUtils;
import alluxio.util.io.PathUtils;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration tests for {@link alluxio.client.block.BlockInStream}.
 */
public final class BufferedBlockInStreamIntegrationTest {
  private static final int MIN_LEN = 0;
  private static final int MAX_LEN = 255;
  private static final int DELTA = 33;

  @ClassRule
  public static LocalAlluxioClusterResource sLocalAlluxioClusterResource =
      new LocalAlluxioClusterResource();
  private static FileSystem sFileSystem;
  private static Configuration sConfiguration;
  private static CreateFileOptions sWriteBoth;
  private static CreateFileOptions sWriteAlluxio;
  private static CreateFileOptions sWriteUnderStore;
  private static String sTestPath;

  @BeforeClass
  public static final void beforeClass() throws Exception {
    sFileSystem = sLocalAlluxioClusterResource.get().getClient();
    sConfiguration = sLocalAlluxioClusterResource.get().getMasterConf();
    sWriteBoth = StreamOptionUtils.getCreateFileOptionsCacheThrough(sConfiguration);
    sWriteAlluxio = StreamOptionUtils.getCreateFileOptionsMustCache(sConfiguration);
    sWriteUnderStore = StreamOptionUtils.getCreateFileOptionsThrough(sConfiguration);
    sTestPath = PathUtils.uniqPath();

    // Create files of varying size and write type to later read from
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (CreateFileOptions op : getOptionSet()) {
        AlluxioURI path = new AlluxioURI(sTestPath + "/file_" + k + "_" + op.hashCode());
        FileSystemTestUtils.createByteFile(sFileSystem, path, op, k);
      }
    }
  }

  private static List<CreateFileOptions> getOptionSet() {
    List<CreateFileOptions> ret = new ArrayList<CreateFileOptions>(3);
    ret.add(sWriteBoth);
    ret.add(sWriteAlluxio);
    ret.add(sWriteUnderStore);
    return ret;
  }

  /**
   * Test {@link alluxio.client.block.BufferedBlockInStream#read()}.
   */
  @Test
  public void readTest1() throws IOException, AlluxioException {
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (CreateFileOptions op : getOptionSet()) {
        AlluxioURI path = new AlluxioURI(sTestPath + "/file_" + k + "_" + op.hashCode());
        for (int i = 0; i < 2; i ++) {
          FileInStream is = sFileSystem.openFile(path, FileSystemTestUtils.toOpenFileOptions(op));
          byte[] ret = new byte[k];
          int value = is.read();
          int cnt = 0;
          while (value != -1) {
            Assert.assertTrue(value >= 0);
            Assert.assertTrue(value < 256);
            ret[cnt++] = (byte) value;
            value = is.read();
          }
          Assert.assertEquals(cnt, k);
          Assert.assertTrue(BufferUtils.equalIncreasingByteArray(k, ret));
          is.close();
        }
      }
    }
  }

  /**
   * Test {@link alluxio.client.block.BufferedBlockInStream#read(byte[])}.
   */
  @Test
  public void readTest2() throws IOException, AlluxioException {
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (CreateFileOptions op : getOptionSet()) {
        AlluxioURI path = new AlluxioURI(sTestPath + "/file_" + k + "_" + op.hashCode());

        FileInStream is = sFileSystem.openFile(path, FileSystemTestUtils.toOpenFileOptions(op));
        byte[] ret = new byte[k];
        Assert.assertEquals(k, is.read(ret));
        Assert.assertTrue(BufferUtils.equalIncreasingByteArray(k, ret));
        is.close();

        is = sFileSystem.openFile(path, FileSystemTestUtils.toOpenFileOptions(op));
        ret = new byte[k];
        Assert.assertEquals(k, is.read(ret));
        Assert.assertTrue(BufferUtils.equalIncreasingByteArray(k, ret));
        is.close();
      }
    }
  }

  /**
   * Test {@link alluxio.client.block.BufferedBlockInStream#read(byte[], int, int)}.
   */
  @Test
  public void readTest3() throws IOException, AlluxioException {
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (CreateFileOptions op : getOptionSet()) {
        AlluxioURI path = new AlluxioURI(sTestPath + "/file_" + k + "_" + op.hashCode());

        FileInStream is = sFileSystem.openFile(path, FileSystemTestUtils.toOpenFileOptions(op));

        byte[] ret = new byte[k / 2];
        Assert.assertEquals(k / 2, is.read(ret, 0, k / 2));
        Assert.assertTrue(BufferUtils.equalIncreasingByteArray(k / 2, ret));
        is.close();

        is = sFileSystem.openFile(path, FileSystemTestUtils.toOpenFileOptions(op));
        ret = new byte[k];
        Assert.assertEquals(k, is.read(ret, 0, k));
        Assert.assertTrue(BufferUtils.equalIncreasingByteArray(k, ret));
        is.close();
      }
    }
  }

  /**
   * Test {@link alluxio.client.block.BufferedBlockInStream#skip(long)}.
   */
  @Test
  public void skipTest() throws IOException, AlluxioException {
    for (int k = MIN_LEN + DELTA; k <= MAX_LEN; k += DELTA) {
      for (CreateFileOptions op : getOptionSet()) {
        AlluxioURI path = new AlluxioURI(sTestPath + "/file_" + k + "_" + op.hashCode());

        FileInStream is = sFileSystem.openFile(path, FileSystemTestUtils.toOpenFileOptions(op));

        Assert.assertEquals(k / 2, is.skip(k / 2));
        Assert.assertEquals(k / 2, is.read());
        is.close();

        is = sFileSystem.openFile(path, FileSystemTestUtils.toOpenFileOptions(op));
        int t = k / 3;
        Assert.assertEquals(t, is.skip(t));
        Assert.assertEquals(t, is.read());
        Assert.assertEquals(t, is.skip(t));
        Assert.assertEquals(2 * t + 1, is.read());
        is.close();
      }
    }
  }
}
