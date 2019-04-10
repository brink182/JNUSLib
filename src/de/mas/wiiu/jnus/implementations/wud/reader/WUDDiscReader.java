/****************************************************************************
 * Copyright (C) 2016-2019 Maschell
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ****************************************************************************/
package de.mas.wiiu.jnus.implementations.wud.reader;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

import de.mas.wiiu.jnus.implementations.wud.WUDImage;
import de.mas.wiiu.jnus.utils.cryptography.AESDecryption;
import lombok.Getter;
import lombok.extern.java.Log;

@Log
public abstract class WUDDiscReader {
    @Getter private final WUDImage image;

    public WUDDiscReader(WUDImage image) {
        this.image = image;
    }

    public InputStream readEncryptedToInputStream(long offset, long size) throws IOException {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream(in);

        new Thread(() -> {
            try {
                readEncryptedToOutputStream(out, offset, size);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "readEncryptedToInputStream@" + this.hashCode()).start();

        return in;
    }

    public byte[] readEncryptedToByteArray(long offset, long fileoffset, long size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        readEncryptedToOutputStream(out, offset + fileoffset, size);
        return out.toByteArray();
    }

    public InputStream readDecryptedToInputStream(long clusterOffset, long offset, long size, byte[] key, byte[] IV, boolean useFixedIV) throws IOException {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream(in);

        new Thread(() -> {
            try {
                readDecryptedToOutputStream(out, clusterOffset, offset, size, key, IV, useFixedIV);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "readDecryptedToInputStream@" + this.hashCode()).start();

        return in;
    }

    public byte[] readDecryptedToByteArray(long offset, long fileoffset, long size, byte[] key, byte[] IV, boolean useFixedIV) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        readDecryptedToOutputStream(out, offset, fileoffset, size, key, IV, useFixedIV);
        return out.toByteArray();
    }

    protected abstract void readEncryptedToOutputStream(OutputStream out, long offset, long size) throws IOException;

    /**
     * 
     * @param readOffset
     *            Needs to be aligned to 0x8000
     * @param key
     * @param IV
     * @return
     * @throws IOException
     */
    public byte[] readDecryptedChunk(long readOffset, byte[] key, byte[] IV) throws IOException {
        int chunkSize = 0x10000;

        byte[] encryptedChunk = readEncryptedToByteArray(readOffset, 0, chunkSize);
        byte[] decryptedChunk = new byte[chunkSize];

        AESDecryption aesDecryption = new AESDecryption(key, IV);
        decryptedChunk = aesDecryption.decrypt(encryptedChunk);

        return decryptedChunk;
    }

    public void readDecryptedToOutputStream(OutputStream outputStream, long clusterOffset, long fileOffset, long size, byte[] key, byte[] IV,
            boolean useFixedIV) throws IOException {
        byte[] usedIV = null;
        if (useFixedIV) {
            usedIV = IV;
            if (IV == null) {
                usedIV = new byte[0x10];
            }
        }

        long usedSize = size;
        long usedFileOffset = fileOffset;
        byte[] buffer;

        long maxCopySize;
        long copySize;

        long readOffset;

        final int BLOCK_SIZE = 0x10000;
        long totalread = 0;

        do {
            long blockNumber = (usedFileOffset / BLOCK_SIZE);
            long blockOffset = (usedFileOffset % BLOCK_SIZE);

            readOffset = clusterOffset + (blockNumber * BLOCK_SIZE);
            // (long)WiiUDisc.WIIU_DECRYPTED_AREA_OFFSET + volumeOffset + clusterOffset + (blockStructure.getBlockNumber() * 0x8000);

            if (!useFixedIV) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(0x10);
                byteBuffer.position(0x08);
                usedIV = byteBuffer.putLong(usedFileOffset >> 16).array();
            }

            buffer = readDecryptedChunk(readOffset, key, usedIV);
            maxCopySize = BLOCK_SIZE - blockOffset;
            copySize = (usedSize > maxCopySize) ? maxCopySize : usedSize;

            try {
                outputStream.write(Arrays.copyOfRange(buffer, (int) blockOffset, (int) (blockOffset + copySize)));
            } catch (IOException e) {
                if (e.getMessage().equals("Pipe closed")) {
                    break;
                } else {
                    throw e;
                }
            }

            totalread += copySize;

            // update counters
            usedSize -= copySize;
            usedFileOffset += copySize;
        } while (totalread < size);

        outputStream.close();
    }

    /**
     * Create a new RandomAccessFileStream
     * 
     * @return
     * @throws FileNotFoundException
     */
    public RandomAccessFile getRandomAccessFileStream() throws FileNotFoundException {
        if (getImage() == null || getImage().getFileHandle() == null) {
            log.warning("No image or image filehandle set.");
            System.exit(1); // TODO: NOOOOOOOOOOOOO
        }
        return new RandomAccessFile(getImage().getFileHandle(), "r");
    }
}
