package hu.dpc.phee.operator.importer;

import hu.dpc.phee.operator.entity.transfer.Transfer;
import hu.dpc.phee.operator.entity.transfer.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InflightTransferManager {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Map<Long, Transfer> inflightTransfers = new ConcurrentHashMap<>();

    @Autowired
    private TransferRepository transferRepository;

    public void transferStarted(Long workflowInstanceKey, Long timestamp, String direction) {
        Transfer transfer = getOrCreateTransfer(workflowInstanceKey);
        Date date=new Date(timestamp);
        logger.info("Started at date:{}",date);
        if (transfer.getStartedAt() == null) {
            transfer.setDirection(direction);
            transfer.setStartedAt(date);
            transferRepository.save(transfer);
        } else {
            logger.debug("transfer {} already started at {}", workflowInstanceKey, transfer.getStartedAt());
        }
    }

    public void transferEnded(Long workflowInstanceKey, Long timestamp) {
        synchronized (inflightTransfers) {
            Transfer transfer = inflightTransfers.remove(workflowInstanceKey);

            if (transfer == null) {
                logger.error("failed to remove in-flight transfer {}", workflowInstanceKey);
                transfer = transferRepository.findByWorkflowInstanceKey(workflowInstanceKey);
                if (transfer == null || transfer.getCompletedAt() != null) {
                    logger.error("completed event arrived for non existent transfer {} or it was already finished!", workflowInstanceKey);
                    return;
                }
            }
            Date endTime=new Date(timestamp);
            logger.info("End time : {}",endTime);
            transfer.setCompletedAt(endTime);
            transferRepository.save(transfer);
            logger.debug("transfer finished {}", transfer.getWorkflowInstanceKey());
        }
    }

    public Transfer getOrCreateTransfer(Long workflowInstanceKey) {
        synchronized (inflightTransfers) {
            Transfer transfer = inflightTransfers.get(workflowInstanceKey);
            if (transfer == null) {
                transfer = transferRepository.findByWorkflowInstanceKey(workflowInstanceKey);
                if (transfer == null) {
                    transfer = new Transfer(workflowInstanceKey); // Sets status to ONGOING
                    logger.debug("started in-flight transfer {}", transfer.getWorkflowInstanceKey());
                }
                inflightTransfers.put(workflowInstanceKey, transfer);
            }
            return transfer;
        }
    }
}