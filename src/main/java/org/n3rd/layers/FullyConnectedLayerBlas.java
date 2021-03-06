
package org.n3rd.layers;

import org.jblas.NativeBlas;
import org.n3rd.Tensor;
import org.n3rd.layers.AbstractLayer;
import org.sgdtk.ArrayDouble;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Fully connected layer
 *
 * Implements a fully connected layer.  Pretty self-explanatory
 *
 * @author dpressel
 */
public class FullyConnectedLayerBlas extends AbstractLayer
{

    protected Tensor z;

    private int outputLength;
    private int inputLength;

    /**
     * This method exists for deserialization from the model
     *
     * @param params The params, which are immediately converted to a Tensor object
     */
    public void setParams(LinkedHashMap<String, Object> params)
    {
        this.weights = new Tensor(params);
    }

    public void setBiasParams(ArrayList<Double> biasParams)
    {
        int sz = biasParams.size();
        biases = new double[sz];
        for (int i = 0; i < sz; ++i)
        {
            biases[i] = biasParams.get(i);
        }
    }

    /**
     * Random initializer for weights
     *
     * @return
     */
    public double rand()
    {
        double stdv = 1. / Math.sqrt(inputLength);
        double stdv2 = stdv * 2;
        return Math.random() * stdv2 - stdv;
    }

    /**
     * Empty constructor (for reincarnating models)
     */
    public FullyConnectedLayerBlas()
    {

    }

    /**
     * Constructor, with given outputLength and input length
     * @param outputLength Output length
     * @param inputLength Input length
     */
    public FullyConnectedLayerBlas(int outputLength, int inputLength)
    {
        this.outputLength = outputLength;
        this.inputLength = inputLength;
        weights = new Tensor(outputLength, this.inputLength);
        weightAccum = new Tensor(outputLength, this.inputLength);
        gradsW = new Tensor(outputLength, this.inputLength);
        biases = new double[outputLength];
        biasGrads = new double[outputLength];

        for (int i = 0, ibase = 0; i < outputLength; ++i, ibase += this.inputLength)
        {
            for (int j = 0; j < this.inputLength; ++j)
            {
                weights.set(ibase + j, rand());
            }
            biases[i] = rand();
        }
        grads = new Tensor(this.inputLength);
        output = new Tensor(outputLength);

    }

    /**
     * Forward prop
     * @param x
     * @return
     */
    @Override
    public Tensor forward(Tensor x)
    {
        this.z = x;
        final ArrayDouble zA = z.getArray();
        final ArrayDouble wA = weights.getArray();
        ArrayDouble oA = output.getArray();

        output.constant(0.);
        NativeBlas.dgemv('N', outputLength, inputLength, 1.0, wA.v, 0, outputLength, zA.v, 0, 1, 1.0, oA.v, 0, 1);
        return output;

    }


    /**
     * Do backprop
     * @param chainGrad layers above's deltas
     * @param y Label
     * @return The deltas for this layer
     */
    @Override
    public Tensor backward(Tensor chainGrad, double y)
    {
        ArrayDouble cgA = chainGrad.getArray();
        ArrayDouble gA = grads.getArray();
        ArrayDouble wA = weights.getArray();
        ArrayDouble gwA = gradsW.getArray();
        ArrayDouble zA = z.getArray();
        grads.constant(0.);

        NativeBlas.dgemv('T', outputLength, inputLength, 1.0, wA.v, 0, outputLength, cgA.v, 0, 1, 1.0, gA.v, 0, 1);
        NativeBlas.dger(outputLength, inputLength, 1.0, cgA.v, 0, 1, zA.v, 0, 1, gwA.v, 0, outputLength);

        for (int i = 0; i < outputLength; ++i)
        {
            biasGrads[i] = chainGrad.at(i);
        }
        return grads;

    }

    public int getOutputLength()
    {
        return outputLength;
    }

    public void setOutputLength(Integer outputLength)
    {
        this.outputLength = outputLength;
    }

    public int getInputLength()
    {
        return inputLength;
    }

    public void setInputLength(Integer inputLength)
    {
        this.inputLength = inputLength;
    }
}
