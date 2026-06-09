package ua.quietlycore.scan.fixtures.outside;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@FilterDef(name = "obj.code", parameters = @ParamDef(name = "code", type = String.class))
@Filter(name = "obj.code", condition = "code = :code")
public class OutsideFilteredEntity
{

   public String code;
}
