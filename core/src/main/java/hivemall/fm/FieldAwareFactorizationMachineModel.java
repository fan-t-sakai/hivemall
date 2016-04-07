/*
 * Hivemall: Hive scalable Machine Learning Library
 *
 * Copyright (C) 2015 Makoto YUI
 * Copyright (C) 2013-2015 National Institute of Advanced Industrial Science and Technology (AIST)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hivemall.fm;

import hivemall.common.EtaEstimator;
import hivemall.utils.lang.NumberUtils;

import java.util.ArrayList;
import java.util.Arrays;

import javax.annotation.Nonnull;

public abstract class FieldAwareFactorizationMachineModel extends FactorizationMachineModel {

    public FieldAwareFactorizationMachineModel(boolean classification, int factor, float lambda0,
            double sigma, long seed, double minTarget, double maxTarget, EtaEstimator eta,
            VInitScheme vInit) {
        super(classification, factor, lambda0, sigma, seed, minTarget, maxTarget, eta, vInit);
    }

    public abstract float getV(@Nonnull Feature x, @Nonnull Object field, int f);

    protected abstract void setV(@Nonnull Feature x, @Nonnull String yField, int f, float nextVif);

    //args require current feature and interacting field
    @Override
    public float getV(Feature x, int f) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void setV(Feature x, int f, float nextVif) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected double predict(Feature[] x) {
        // w0
        double ret = getW0();
        // W
        for (Feature e : x) {
            double xj = e.getValue();
            float w = getW(e);
            double wx = w * xj;
            ret += wx;
        }
        // V
        for (int f = 0, k = _factor; f < k; f++) {
            for (int i = 0; i < x.length; ++i) {
                for (int j = i + 1; j < x.length; ++j) {
                    Feature ei = x[i];
                    Feature ej = x[j];
                    double xi = ei.getValue();
                    double xj = ej.getValue();
                    float vijf = getV(ei, ej.getField(), f);
                    float vjif = getV(ej, ei.getField(), f);
                    ret += vijf * vjif * xi * xj;
                    assert (!Double.isNaN(ret));
                }
            }
        }
        if (!NumberUtils.isFinite(ret)) {
            throw new IllegalStateException("Detected " + ret
                    + " in predict. We recommend to normalize training examples.\n"
                    + "Dumping variables ...\n" + super.varDump(x));
        }
        return ret;
    }

    void updateV(final double dloss, @Nonnull final Feature x, final int f, final double sumViX,
            final float eta, Object field) {
        final double Xi = x.getValue();
        float currentV = getV(x, field, f);
        double h = Xi * sumViX;
        float gradV = (float) (dloss * h);
        float LambdaVf = getLambdaV(f);
        float nextV = currentV - eta * (gradV + 2.f * LambdaVf * currentV);
        if (!NumberUtils.isFinite(nextV)) {
            throw new IllegalStateException("Got " + nextV + " for next V" + f + '['
                    + x.getFeature() + "]\n" + "Xi=" + Xi + ", Vif=" + currentV + ", h=" + h
                    + ", gradV=" + gradV + ", lambdaVf=" + LambdaVf + ", dloss=" + dloss
                    + ", sumViX=" + sumViX);
        }
        setV(x, field.toString(), f, nextV);
    }

    double[][][] sumVfX(Feature[] x, ArrayList<Object> fieldList) {
        final int k = _factor;
        final int listSize = fieldList.size();
        final int xSize = x.length;
        final double[][][] ret = new double[xSize][listSize][k];
        for (int i = 0; i < xSize; ++i) {
            for (int a = 0; a < listSize; ++a) {
                for (int f = 0; f < k; f++) {
                    ret[i][a][f] = sumVfX(x, i, fieldList.get(a), f);
                }
            }
        }
        return ret;
    }

    private double sumVfX(@Nonnull final Feature[] x, final int i, @Nonnull final Object a,
            final int f) {
        double ret = 0.d;
        //find all other features whose field matches a
        for (Feature e : x) {
            if (x[i].getFeature().equals(e.getFeature())) {//ignore x[i] = e
                continue;
            }
            if (e.getField().equals(a)) {//multiply x_e and v_d,field(e),f
                double xj = x[i].getValue();
                float Vjf = getV(e, x[i].getField(), f);
                ret += Vjf * xj;
            }
        }
        if (!NumberUtils.isFinite(ret)) {
            throw new IllegalStateException("Got " + ret + " for sumV[ " + i + "][ " + f + "]X.\n"
                    + "x = " + Arrays.toString(x));
        }
        return ret;
    }
}
