package ua.quietlycore.scan.fixtures.allowed;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@FilterDef(name = "obj.status", parameters = @ParamDef(name = "status", type = String.class))
@Filter(name = "obj.status", condition = "status = :status")
public class FilteredEntity
{

   public String status;
}
