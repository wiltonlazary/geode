/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire.internal.cache.tier.sockets.command;

import java.io.IOException;

import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.internal.cache.TXId;
import com.gemstone.gemfire.internal.cache.TXManagerImpl;
import com.gemstone.gemfire.internal.cache.TXStateProxy;
import com.gemstone.gemfire.internal.cache.tier.Command;
import com.gemstone.gemfire.internal.cache.tier.sockets.BaseCommand;
import com.gemstone.gemfire.internal.cache.tier.sockets.Message;
import com.gemstone.gemfire.internal.cache.tier.sockets.ServerConnection;

/**
 * Command for performing Rollback on the server
 * @author sbawaska
 */
public class RollbackCommand extends BaseCommand {

  private final static RollbackCommand singleton = new RollbackCommand();
  
  public static Command getCommand() {
    return singleton;
  }
  
  private RollbackCommand() {
  }
  
  @Override
  public void cmdExecute(Message msg, ServerConnection servConn, long start)
      throws IOException, ClassNotFoundException, InterruptedException {
    servConn.setAsTrue(REQUIRES_RESPONSE);
    TXManagerImpl txMgr = (TXManagerImpl)servConn.getCache().getCacheTransactionManager();
    InternalDistributedMember client = (InternalDistributedMember) servConn.getProxyID().getDistributedMember();
    int uniqId = msg.getTransactionId();
    TXId txId = new TXId(client, uniqId);
    if (txMgr.isHostedTxRecentlyCompleted(txId)) {
      if (logger.isDebugEnabled()) {
        logger.debug("TX: found a recently rolled back tx: {}", txId);
        sendRollbackReply(msg, servConn);
        txMgr.removeHostedTXState(txId);
        return;
      }
    }
    final TXStateProxy txState = txMgr.getTXState();
    try {
      if (txState != null) {
        txId = txState.getTxId();
        txMgr.rollback();
        sendRollbackReply(msg, servConn);
      }
    } catch (Exception e) {
      writeException(msg, e, false, servConn);
      servConn.setAsTrue(RESPONDED);
    } finally {
      if (logger.isDebugEnabled()) {
        logger.debug("TX: removing tx state for {}", txId);
      }
      if (txId != null) {
        TXStateProxy proxy = txMgr.removeHostedTXState(txId);
        if (logger.isDebugEnabled()) {
          logger.debug("TX: removed tx state proxy {}", proxy);
        }
      }
    }
  }

  private void sendRollbackReply(Message msg, ServerConnection servConn)
      throws IOException {
    writeReply(msg, servConn);
    servConn.setAsTrue(RESPONDED);
  }

}
