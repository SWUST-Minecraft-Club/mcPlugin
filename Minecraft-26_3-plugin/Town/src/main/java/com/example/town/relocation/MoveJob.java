package com.example.town.relocation;

import com.example.town.economy.Payment;
import java.util.*;

/** 一次已确认的工程。工程序号也用作钱包业务号，重启不会产生第二次扣费。 */
public record MoveJob(String id,String town,UUID actor,String actorName,MoveTransform transform,
                      String plot,long plotRevision,boolean hall,MoveTransform.Point hallBefore,
                      float yaw,float pitch,String currency,long cents,long createdAt,Stage stage,String error) {
    public enum Stage { PREPARED, APPLYING, REVIEW, ACCEPTING, ACCEPTED, ROLLING_BACK, ROLLED_BACK, CANCELLED }
    public MoveJob {
        UUID.fromString(id);Objects.requireNonNull(actor);Objects.requireNonNull(transform);Objects.requireNonNull(stage);
        if(transform.source().volume()>262144 || town==null || town.isBlank() || actorName==null || cents<0 || createdAt<0 || currency==null
                || (cents>0 && currency.isBlank()) || plotRevision<0 || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("迁移工程数据无效");
        plot=plot==null?"":plot;error=error==null?"":error;
        if(hall && hallBefore==null) throw new IllegalArgumentException("迁址缺少原核心坐标");
    }
    public MoveJob at(Stage next) {return with(next,"");}
    public MoveJob with(Stage next,String message) {
        return new MoveJob(id,town,actor,actorName,transform,plot,plotRevision,hall,hallBefore,yaw,pitch,currency,cents,createdAt,next,message);
    }
    public boolean terminal() {return stage==Stage.ACCEPTED || stage==Stage.ROLLED_BACK || stage==Stage.CANCELLED;}
    public Payment payment() {
        return new Payment(id,"move",actor,actorName,currency,cents,null,"",0,Map.of("town",town,"job",id),createdAt,Payment.Stage.REQUESTED);
    }
}
