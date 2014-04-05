package uk.co.epii.conservatives.robertwalpole;

import com.vividsolutions.jts.geom.MultiPolygon;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.epii.spencerperceval.tuple.Triple;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * User: James Robinson
 * Date: 31/03/2014
 * Time: 15:27
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            findChildrenInParent(args[1], Arrays.copyOfRange(args, 2, args.length), args[0]);
        }
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static void findChildrenInParent(String parentFile, String[] childFiles, String writeTo) throws IOException {
        LOG.debug("parentFile: {}", parentFile);
        LOG.debug("writeTo: {}", writeTo);
        for (int i = 0; i < childFiles.length; i++) {
            LOG.debug("childFiles[{}]: {}", i, childFiles[i]);
        }
        DataSet parents =
                DataSet.createFromFile(new File(parentFile));
        DataSet[] children = new DataSet[childFiles.length];
        for (int i = 0; i < childFiles.length; i++) {
            children[i] = DataSet.createFromFile(new File(childFiles[i]));
        }

        SimpleFeatureIterator councilsIterator = parents.getFeatureSource().getFeatures().features();
        List<Triple<String, MultiPolygon, List<String>>> allWards = new ArrayList<Triple<String, MultiPolygon, List<String>>>();
        Triple<String, MultiPolygon, List<String>> unknown =
                new Triple<String, MultiPolygon, List<String>>("UNKNOWN", null, new ArrayList<String>());
        while (councilsIterator.hasNext()) {
            SimpleFeature council = councilsIterator.next();
            MultiPolygon councilPolygon = (MultiPolygon)council.getAttribute("the_geom");
            String councilName = (String)council.getAttribute("NAME");
            LOG.info("Creating {}", councilName);
            allWards.add(new Triple<String, MultiPolygon, List<String>>(councilName, councilPolygon, new ArrayList<String>()));
        }
        Triple<String, MultiPolygon, List<String>> mostRecent = null;
        for (DataSet child : children) {
            SimpleFeatureIterator wardIterator = child.getFeatureSource().getFeatures().features();
            int i = 0;
            toNextWard: while (wardIterator.hasNext()) {
                SimpleFeature ward = wardIterator.next();
                MultiPolygon wardMultiPolygon = (MultiPolygon)ward.getAttribute("the_geom");
                String wardName = (String)ward.getAttribute("NAME");
                if (wardName.matches("^[ \t]*$")) {
                    continue;
                }
                if (i++ % 100 == 0) {
                    LOG.info("Seeking {}", wardName);
                }
                if (mostRecent != null && mostRecent.getSecond().contains(wardMultiPolygon)) {
                    mostRecent.getThird().add(wardName);
                    continue toNextWard;
                }
                for (Triple<String, MultiPolygon, List<String>> triple : allWards) {
                    if (triple.getSecond().contains(wardMultiPolygon)) {
                        mostRecent = triple;
                        triple.getThird().add(wardName);
                        continue toNextWard;
                    }
                }
                unknown.getThird().add(wardName);
                LOG.error("Failed to find a parent for {}", wardName);
            }
        }
        for (Triple<String, MultiPolygon, List<String>> triple : allWards) {
            Collections.sort(triple.getThird());
        }
        Collections.sort(allWards, new Comparator<Triple<String, MultiPolygon, List<String>>>() {
            @Override
            public int compare(Triple<String, MultiPolygon, List<String>> o1, Triple<String, MultiPolygon, List<String>> o2) {
                return o1.getFirst().compareTo(o2.getFirst());
            }
        });
        allWards.add(unknown);
        FileWriter fileWriter = new FileWriter(writeTo, false);
        PrintWriter printWriter = new PrintWriter(fileWriter, true);
        for (Triple<String, MultiPolygon, List<String>> councilTriple : allWards) {
            if (councilTriple.getThird().isEmpty()) continue;
            printWriter.println(String.format("==%s", councilTriple.getFirst()));
            for (String ward : councilTriple.getThird()) {
                printWriter.println(String.format("=%s", ward));
            }
        }
        printWriter.close();
        fileWriter.close();
    }
}
