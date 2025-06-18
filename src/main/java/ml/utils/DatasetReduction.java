package ml.utils;

import weka.core.Attribute;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

public class DatasetReduction {

    /**
     * Rimuove una feature dal dataset dato il suo nome.
     * Se la feature non è presente, restituisce il dataset invariato.
     */
    public static Instances removeAttributeByName(Instances data, String attributeName) throws Exception {
        Attribute attr = data.attribute(attributeName);
        if (attr == null) {
            return data; // l'attributo non esiste, non serve rimuovere nulla
        }

        Remove remove = new Remove();
        remove.setAttributeIndicesArray(new int[]{attr.index()});
        remove.setInputFormat(data);
        return Filter.useFilter(data, remove);
    }
}
