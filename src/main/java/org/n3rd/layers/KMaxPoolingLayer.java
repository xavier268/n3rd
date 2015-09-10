package org.n3rd.layers;

import org.n3rd.Tensor;
import org.n3rd.util.IntCube;
import org.sgdtk.DenseVectorN;
import org.sgdtk.Offset;
import org.sgdtk.VectorN;

import java.util.*;

/**
 * K-max pooling, a generalization of max-pooling over time, where we take the top K values
 *
 * K-max pooling layer implements temporal max pooling, selecting up to K max features.  This is the approach used
 * in Kalchbrenner & Blunsom for their CNN sentence classification.  When K is 1, it simply becomes max-pooling over
 * time.
 *
 * The current implementation just uses builtin Java data structures, and isnt likely to be particularly optimal
 * and can likely be simplified quite a bit.
 *
 * @author dpressel
 */
public class KMaxPoolingLayer implements Layer
{

    private int k;
    private int embeddingSz;
    private int featureMapSz;
    int numFrames;
    IntCube origin;


    /**
     * Default constructor, used prior to rehydrating model from file
     */
    public KMaxPoolingLayer()
    {

    }

    /**
     * Constructor for training
     * @param k The number of max values to use in each embedding
     * @param featureMapSz This is the number of feature maps
     * @param embedSz This is the embedding space, e.g, for some word2vec input, this might be something like 300
     */
    public KMaxPoolingLayer(int k, int featureMapSz, int embedSz)
    {
        this.k = k;
        this.embeddingSz = embedSz;
        this.featureMapSz = featureMapSz;
    }

    public int getK()
    {
        return k;
    }

    public void setK(Integer k)
    {
        this.k = k;
    }

    public int getEmbeddingSz()
    {
        return embeddingSz;
    }

    public void setEmbeddingSz(Integer embeddingSz)
    {
        this.embeddingSz = embeddingSz;
    }

    public int getFeatureMapSz()
    {
        return featureMapSz;
    }

    public void setFeatureMapSz(Integer featureMapSz)
    {
        this.featureMapSz = featureMapSz;
    }

    public static final class MaxValueComparator implements Comparator<Offset>
    {
        @Override
        public int compare(Offset o1, Offset o2)
        {
            //return o1.index;
            return Double.compare(o2.value, o1.value);
        }
    }
    public static final class MinIndexComparator implements Comparator<Offset>
    {
        @Override
        public int compare(Offset o1, Offset o2)
        {
            //return o1.index;
            return Integer.compare(o1.index, o2.index);
        }
    }

    @Override
    public VectorN forward(VectorN x)
    {

        DenseVectorN denseVectorN = (DenseVectorN)x;
        double[] xarray = denseVectorN.getX();
        numFrames = xarray.length/embeddingSz/featureMapSz;

        Tensor output = new Tensor(featureMapSz, k, embeddingSz);
        origin = new IntCube(featureMapSz, k, embeddingSz);
        for (int i = 0; i < origin.h * origin.w * origin.l; ++i)
        {
            origin.d[i] = -100;
        }

        for (int l = 0; l < featureMapSz; ++l)
        {
            for (int j = 0; j < embeddingSz; ++j)
            {

                PriorityQueue<Offset> offsets = new PriorityQueue<Offset>(new MaxValueComparator());

                for (int i = 0; i < numFrames; ++i)
                {
                    int inAddr = (l * numFrames + i) * embeddingSz + j;

                    offsets.add(new Offset(inAddr, xarray[inAddr]));

                }
                List<Offset> offsetList = new ArrayList<Offset>(k);
                Iterator<Offset> iterator = offsets.iterator();
                for (int i = 0; i < k; ++i)
                {
                    if (iterator.hasNext())
                    {
                        offsetList.add(iterator.next());
                    }
                    //else
                    //{
                    //    throw new RuntimeException("Not enough offsets for K = " + k + ". Found " + i);
                    //}
                }
                offsetList.sort(new MinIndexComparator());
                int sz = offsetList.size();
                for (int i = 0; i < sz; ++i)
                {
                    int outAddr = (l * k + i) * embeddingSz + j;
                    origin.d[outAddr] = offsetList.get(i).index;
                    output.d[outAddr] = offsetList.get(i).value;
                }
            }

        }
        return new DenseVectorN(output.d);
    }

    // Since the output and input are the same for the max value, we can just apply the
    // max-pool value from the output
    @Override
    public VectorN backward(VectorN chainGrad, double y)
    {
        Tensor input = new Tensor(featureMapSz, numFrames, embeddingSz);

        double[] chainGradX = ((DenseVectorN) chainGrad).getX();
        for (int l = 0; l < featureMapSz; ++l)
        {
            for (int i = 0; i < k; ++i)
            {
                for (int j = 0; j < embeddingSz; ++j)
                {

                    int outAddr = (l * k + i) * embeddingSz + j;
                    int inAddr = origin.d[outAddr];
                    if (inAddr == -100)
                    {
                        continue;
                    }
                    input.d[inAddr] = chainGradX[outAddr];
                }
            }
        }
        return new DenseVectorN(input.d);
    }

    // We have no params in this layer
    @Override
    public Tensor getParamGrads()
    {
        return null;
    }

    @Override
    public Tensor getParams()
    {
        return null;
    }

    @Override
    public double[] getBiasGrads()
    {
        return null;
    }

    @Override
    public double[] getBiasParams()
    {
        return null;
    }
}
