package blog.mmap_and_jvm_crash;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static blog.mmap_and_jvm_crash.MMap_Crash.file;

/**
 * Demonstrates JVM crash on continuous allocation
 * when free disk space runs out.
 */
public class MMap_Crash_RAF_setLength {

    public static void main(String[] args) throws IOException {
        byte[] buffer = new byte[1024*1024];

        //a
        RandomAccessFile raf = new RandomAccessFile(file, "rws");
        FileChannel channel = raf.getChannel();

        //allocate in infinite cycle
        for(long offset=0; ; offset+=buffer.length){

            //set file size using RandomAccessFile
            raf.setLength(offset+buffer.length);

            ByteBuffer mappedBuffer = channel.map(
                    FileChannel.MapMode.READ_WRITE,
                    offset,
                    offset+buffer.length);

            //this causes JVM crash if there is no free disk space and delayed write fails
            mappedBuffer.put(buffer, 0, buffer.length);
        }
        //z
    }
}
