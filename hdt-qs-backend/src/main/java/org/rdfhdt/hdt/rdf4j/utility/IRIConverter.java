package org.rdfhdt.hdt.rdf4j.utility;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.AbstractValueFactoryHDT;
import org.eclipse.rdf4j.model.impl.SimpleIRIHDT;
import org.eclipse.rdf4j.model.impl.SimpleLiteralHDT;
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory;
import org.rdfhdt.hdt.dictionary.impl.MultipleBaseDictionary;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.hdt.HDT;

public class IRIConverter {
    private HDT hdt;
    private ValueFactory valueFactory;
    private ValueFactory tempFactory;
    public IRIConverter(HDT hdt){
        this.hdt = hdt;
        this.valueFactory = new AbstractValueFactoryHDT(this.hdt);
        this.tempFactory = new MemValueFactory();
    }
    public Resource getIRIHdtSubj(Resource subj){
        String iriString = subj.toString();
        long id = -1;
        int position = -1;
        if(iriString.startsWith(("http://hdt.org/"))){
            iriString = iriString.replace("http://hdt.org/","");
            if(iriString.startsWith("SO")){
                id = Long.parseLong(iriString.substring(2));
                position = SimpleIRIHDT.SHARED_POS;
            }else if(iriString.startsWith("S")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.SUBJECT_POS;
            }else if(iriString.startsWith("P")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.PREDICATE_POS;
            }else if(iriString.startsWith("O")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.OBJECT_POS;
            }
            return new SimpleIRIHDT(this.hdt,position,id);
        }else{ // string was not converted upon insert - iriString the real IRI
            return subj;
        }
    }
    public IRI getIRIHdtPred(IRI pred){
        String iriString = pred.toString();
        long id = -1;
        int position = -1;
        if(iriString.startsWith(("http://hdt.org/"))){
            iriString = iriString.replace("http://hdt.org/","");
            if(iriString.startsWith("P")) {
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.PREDICATE_POS;
            }else if(iriString.startsWith("SO")){
                id = Long.parseLong(iriString.substring(2));
                position = SimpleIRIHDT.SHARED_POS;
            }else if(iriString.startsWith("S")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.SUBJECT_POS;
            }else if(iriString.startsWith("O")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.OBJECT_POS;
            }
            return new SimpleIRIHDT(this.hdt,position,id);
        }else{ // string was not converted upon insert - iriString the real IRI
            return pred;
        }
    }
    public Value getIRIHdtObj(Value object){
        String iriString = object.toString();
        long id = -1;
        int position = -1;
        if(iriString.startsWith(("http://hdt.org/"))){
            iriString = iriString.replace("http://hdt.org/","");
            if(iriString.startsWith("SO")){
                id = Long.parseLong(iriString.substring(2));
                position = SimpleIRIHDT.SHARED_POS;
            }else if(iriString.startsWith("O")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.OBJECT_POS;
            }else if(iriString.startsWith("P")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.PREDICATE_POS;
            }else if(iriString.startsWith("S")){
                id = Long.parseLong(iriString.substring(1));
                position = SimpleIRIHDT.SUBJECT_POS;
            }
            if(isLiteral(id)){
                return new SimpleLiteralHDT(this.hdt,id,this.valueFactory);
            }else {
                return new SimpleIRIHDT(this.hdt, position, id);
            }
        }else{ // string was not converted upon insert - iriString the real IRI
            return object;
        }
    }


    public Resource convertSubj(Resource subj){
        Resource newSubj = null;
        if(subj != null) {
            if (subj instanceof SimpleIRIHDT) {
                long id = ((SimpleIRIHDT) subj).getId();
                long position = ((SimpleIRIHDT) subj).getPostion();
                if (id != -1) {
                    String prefix = "http://hdt.org/";
                    if(position == SimpleIRIHDT.PREDICATE_POS){
                        String translate =
                                hdt.getDictionary()
                                        .idToString(id,
                                                TripleComponentRole.PREDICATE)
                                        .toString();
                        id = hdt.getDictionary().stringToId(translate, TripleComponentRole.SUBJECT);
                    }else if(position == SimpleIRIHDT.OBJECT_POS){
                        String translate =
                                hdt.getDictionary()
                                        .idToString(id,
                                                TripleComponentRole.OBJECT)
                                        .toString();
                        id = hdt.getDictionary().stringToId(translate, TripleComponentRole.SUBJECT);
                    }
                    if(id == -1){
                        newSubj = subj;
                    }else {
                        if(id <= this.hdt.getDictionary().getNshared()){
                            prefix += "SO";
                            String subjIdentifier = prefix + id;
                            newSubj = new SimpleIRIHDT(hdt, subjIdentifier, SimpleIRIHDT.SHARED_POS, id);
                        }
                        else {
                            prefix += "S";
                            String subjIdentifier = prefix + id;
                            newSubj = new SimpleIRIHDT(hdt, subjIdentifier, SimpleIRIHDT.SUBJECT_POS, id);
                        }
                    }
                } else {
                    newSubj = subj;
                }
            } else { // upon insertion need to convert string to ID
                String subjStr = subj.toString();
                long subjId = this.hdt.getDictionary().stringToId(subjStr, TripleComponentRole.SUBJECT);
                if (subjId != -1) {
                    if (subjId <= this.hdt.getDictionary().getNshared()) {
                        newSubj = new SimpleIRIHDT(hdt,"http://hdt.org/SO" + subjId,SimpleIRIHDT.SHARED_POS,subjId);
                    } else {
                        newSubj = new SimpleIRIHDT(hdt,"http://hdt.org/S" + subjId,SimpleIRIHDT.SUBJECT_POS,subjId);
                    }
                } else {
                    newSubj = subj;
                }
            }
        }
        return newSubj;
    }
    public IRI convertPred(IRI pred){
        IRI newPred = null;
        if(pred != null) {
            if (pred instanceof SimpleIRIHDT) {
                long id = ((SimpleIRIHDT) pred).getId();
                long position = ((SimpleIRIHDT) pred).getPostion();
                if (id != -1) {
                    String prefix = "http://hdt.org/";
                    prefix += "P";
                    if (position == SimpleIRIHDT.SHARED_POS || position == SimpleIRIHDT.SUBJECT_POS) {
                        String translate =
                                hdt.getDictionary()
                                        .idToString(id,
                                                TripleComponentRole.SUBJECT)
                                        .toString();
                        id = hdt.getDictionary().stringToId(translate, TripleComponentRole.PREDICATE);
                    } else if (position == SimpleIRIHDT.OBJECT_POS) {
                        String translate =
                                hdt.getDictionary()
                                        .idToString(id,
                                                TripleComponentRole.OBJECT)
                                        .toString();
                        id = hdt.getDictionary().stringToId(translate, TripleComponentRole.PREDICATE);
                    }
                    String predIdentifier = prefix + id;
                    if(id == -1){
                        newPred = pred;
                    }else
                        newPred = new SimpleIRIHDT(hdt,predIdentifier,SimpleIRIHDT.PREDICATE_POS,id);
                } else {
                    newPred = pred;
                }
            } else {
                String predStr = pred.toString();
                long predId = this.hdt.getDictionary().stringToId(predStr, TripleComponentRole.PREDICATE);
                if (predId != -1) {
                    newPred = new SimpleIRIHDT(hdt,"http://hdt.org/P" + predId,SimpleIRIHDT.PREDICATE_POS,predId);
                } else {
                    newPred = pred;
                }
            }
        }
        return newPred;
    }
    public Value convertObj(Value obj){
        Value newObj = null;
        if(obj != null) {
            if (obj instanceof SimpleIRIHDT) {
                long id = ((SimpleIRIHDT) obj).getId();
                long position = ((SimpleIRIHDT) obj).getPostion();
                if (id != -1) {
                    String prefix = "http://hdt.org/";
                    if(position == SimpleIRIHDT.SUBJECT_POS || position == SimpleIRIHDT.SHARED_POS){
                        String translate =
                                hdt.getDictionary()
                                        .idToString(id,
                                                TripleComponentRole.SUBJECT)
                                        .toString();
                        id = hdt.getDictionary().stringToId(translate, TripleComponentRole.OBJECT);
                    }else if(position == SimpleIRIHDT.PREDICATE_POS){
                        String translate =
                                hdt.getDictionary()
                                        .idToString(id,
                                                TripleComponentRole.PREDICATE)
                                        .toString();
                        id = hdt.getDictionary().stringToId(translate, TripleComponentRole.OBJECT);
                    }
                    if(id == -1){
                        newObj = obj;
                    }else {
                        if(id <= this.hdt.getDictionary().getNshared()){
                            prefix += "SO";
                            String objIdentifier = prefix + id;
                            newObj = new SimpleIRIHDT(hdt, objIdentifier, SimpleIRIHDT.SHARED_POS, id);
                        }
                        else {
                            prefix += "O";
                            String objIdentifier = prefix + id;
                            newObj = new SimpleIRIHDT(hdt, objIdentifier, SimpleIRIHDT.OBJECT_POS, id);
                        }
                    }
                } else {
                    newObj = obj;
                }
            } else {
                String objStr = obj.toString();
                long objId = this.hdt.getDictionary().stringToId(objStr, TripleComponentRole.OBJECT);
                if (objId != -1) {
                    if (objId <= this.hdt.getDictionary().getNshared()) {

                        newObj = new SimpleIRIHDT(hdt,"http://hdt.org/SO" + objId,SimpleIRIHDT.SHARED_POS,objId);
                    } else {
                        newObj = new SimpleIRIHDT(hdt,"http://hdt.org/O" + objId,SimpleIRIHDT.OBJECT_POS,objId);
                    }
                } else {
                    newObj = obj;
                }
            }
        }
        return newObj;
    }
    public Literal convertLiteral(Literal obj){
        String objStr = obj.toString();
        if(objStr.startsWith("http://hdt.org/")){
            objStr = objStr.replace("http://hdt.org/","");
            long id = Long.parseLong(objStr.substring(1));
            return new SimpleLiteralHDT(hdt,id,this.valueFactory);
        }else{
            return obj;
        }
    }
    private boolean isLiteral(long id){
        //MultipleBaseDictionary dictionary = (;
        String dataType = this.hdt.getDictionary().dataTypeOfId(id);
        return !dataType.equals("NO_DATATYPE") && !dataType.equals("section");
    }
}
