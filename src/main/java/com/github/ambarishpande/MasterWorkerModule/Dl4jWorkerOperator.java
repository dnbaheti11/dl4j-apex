package com.github.ambarishpande.MasterWorkerModule;

import com.datatorrent.api.Context;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.common.util.BaseOperator;

import org.apache.commons.lang.NullArgumentException;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Created by @ambarishpande on 14/1/17.
 */
public class Dl4jWorkerOperator extends BaseOperator
{

  private long windowId;
  private static final Logger LOG = LoggerFactory.getLogger(Dl4jWorkerOperator.class);

  private MultiLayerConfiguration conf;
  private MultiLayerNetwork model;
  private boolean hold;
  private ArrayList<DataSet> buffer;

  public transient DefaultInputPort<DataSet> dataPort = new DefaultInputPort<DataSet>()
  {
    @Override
    public void process(DataSet data)
    {
      try {
        if (!model.isInitCalled()) {
          model.init();
        }
        
        if(hold) {
            LOG.info("Storing Data in Buffer...");
            buffer.add(data);
        }

        else{
            if(!(buffer.isEmpty())) {
                for ( DataSet d : buffer) {
                    model.fit(d);
                    buffer.remove(d);
                  LOG.info("Fitting over buffered datasets");
                }
            }
            model.fit(data);
            LOG.info("Fitting over normal dataset...");
        }


      } catch (NullArgumentException e) {
          LOG.error("Null Pointer exception" + e.getMessage());
      }

    }
  };

  public transient DefaultInputPort<INDArray> controlPort = new DefaultInputPort<INDArray>()
  {
    @Override
    public void process(INDArray parameters)
    {

      LOG.info("Parameters received from Master...");
      model.setParams(parameters);
      hold = false;
    }
  };

  public transient DefaultOutputPort<INDArray> output = new DefaultOutputPort<INDArray>();

  public void setup(Context.OperatorContext context)
  {
    LOG.info("Setup Started...");
    model = new MultiLayerNetwork(conf);
    model.init();
    hold = false;
    buffer = new ArrayList<DataSet>();
    LOG.info(" Worker ID : "+context.getId());
    LOG.info("Setup Completed...");
  }

  public void beginWindow(long windowId)
  {
    //    Do Nothing
    LOG.info("Window Id:" + windowId);
    this.windowId = windowId;
  }

  public void endWindow()
  {

    if(windowId%10 == 0)
    {
      INDArray newParams = model.params();
      LOG.info("New Params : " + newParams.toString());
      output.emit(newParams);
      hold = true;
      LOG.info("New Parameters given to ParameterAverager...");
    }
  }

  public void setConf(MultiLayerConfiguration conf)
  {
    this.conf = conf;
  }
  public MultiLayerNetwork getModel()
  {
    return model;
  }
}

