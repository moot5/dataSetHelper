package com.otaliastudios.cameraview.demo;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageParser;
import org.apache.commons.imaging.formats.jpeg.decoder.JpegDecoder;
import org.apache.commons.imaging.formats.tiff.fieldtypes.FieldType;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;

import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;

public class ExifDataHandler {

    public static void addData(File aJpeg, float[] accelerometerReadings, float [] gyroscopeReadings) throws ImageWriteException, IOException, ImageReadException {
    //public static void addData(File aJpeg) throws ImageWriteException, IOException, ImageReadException {

        System.out.println(aJpeg.getAbsolutePath());
        TiffOutputSet outputSet = null;
        final ImageMetadata md = Imaging.getMetadata(aJpeg);
        final JpegImageMetadata jpegmd = (JpegImageMetadata) md;
        TiffOutputDirectory exifDir = null;

        if(null != jpegmd) {
            final TiffImageMetadata exif = jpegmd.getExif();

            if (null != exif) {
                outputSet = exif.getOutputSet();
                exifDir = outputSet.getExifDirectory();
                System.out.println(exifDir.getFields().toString());
                System.out.println("exif exists");
            }
        }
        else if (null == outputSet) {
            outputSet = new TiffOutputSet();
            System.out.println("no exif");
            exifDir = outputSet.getOrCreateExifDirectory();
        }

        // replace this with actual results!
        //String astring = "AX:-0.23311326,AY:0.0923843086,AZ:9.874436,GX:-0.0014088068,GY:-1.7180527E4,GZ:-0.0026305355";

        String accelerometerString = Arrays.toString(accelerometerReadings);
        String gyroscopeString = Arrays.toString(gyroscopeReadings);
        String sensorString = ("A:"+accelerometerString + ",G:" + gyroscopeString);
        byte[] sensorStringBytes = sensorString.getBytes();
        TiffOutputField outputField = new TiffOutputField(ExifTagConstants.EXIF_TAG_USER_COMMENT, FieldType.ASCII, sensorStringBytes.length, sensorStringBytes);

        //byte[] byteArray = astring.getBytes();
        //TiffOutputField outputField = new TiffOutputField(ExifTagConstants.EXIF_TAG_SOFTWARE, FieldType.ASCII, byteArray.length, byteArray);
        exifDir.add(outputField);

        System.out.println(exifDir.getFields() + ": exif field types");

        String exifFileName = ("/sdcard/Frames/exif_" + aJpeg.getName());
        File exifFile = new File(exifFileName);

        try(OutputStream outputStream = new FileOutputStream(exifFile)) {
            new ExifRewriter().updateExifMetadataLossless(aJpeg, outputStream, outputSet);
            outputStream.flush();
        }
    }

}
