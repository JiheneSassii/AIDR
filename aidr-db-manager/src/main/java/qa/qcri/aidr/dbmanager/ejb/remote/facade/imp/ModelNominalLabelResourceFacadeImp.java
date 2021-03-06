/**
 * Implements operations for managing the model_nominal_label table of the aidr_predict DB
 * 
 * @author Koushik
 */
package qa.qcri.aidr.dbmanager.ejb.remote.facade.imp;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;

import org.apache.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.criterion.Restrictions;

import qa.qcri.aidr.common.exception.PropertyNotSetException;
import qa.qcri.aidr.dbmanager.dto.DocumentDTO;
import qa.qcri.aidr.dbmanager.dto.ModelNominalLabelDTO;
import qa.qcri.aidr.dbmanager.ejb.local.facade.impl.CoreDBServiceFacadeImp;
import qa.qcri.aidr.dbmanager.ejb.remote.facade.DocumentResourceFacade;
import qa.qcri.aidr.dbmanager.ejb.remote.facade.ModelNominalLabelResourceFacade;
import qa.qcri.aidr.dbmanager.ejb.remote.facade.ModelResourceFacade;
import qa.qcri.aidr.dbmanager.entities.model.Model;
import qa.qcri.aidr.dbmanager.entities.model.ModelFamily;
import qa.qcri.aidr.dbmanager.entities.model.ModelNominalLabel;
import qa.qcri.aidr.dbmanager.entities.model.NominalLabel;

@Stateless(name = "ModelNominalLabelResourceFacadeImp")
public class ModelNominalLabelResourceFacadeImp extends CoreDBServiceFacadeImp<ModelNominalLabel, Long> implements ModelNominalLabelResourceFacade {

	private Logger logger = Logger.getLogger("db-manager-log");

	@EJB
	private ModelResourceFacade modelEJB;

	@EJB
	private DocumentResourceFacade documentEJB;

	protected ModelNominalLabelResourceFacadeImp() {
		super(ModelNominalLabel.class);
	}

	@Override
	public List<ModelNominalLabelDTO> getAllModelNominalLabels() {
		List<ModelNominalLabelDTO> dtoList = new ArrayList<ModelNominalLabelDTO>();
		try {
			List<ModelNominalLabel> list = this.getAll();
			if (list != null && !list.isEmpty()) {
				logger.info("Fetched list size: " + list.size());
				for (ModelNominalLabel m : list) {
					dtoList.add(new ModelNominalLabelDTO(m));
				}
			}
			return dtoList;
		} catch (Exception e) {
			logger.error("Error in getAllModelNominalLabels.");
			return null;
		}
	}

	@Override
	public List<ModelNominalLabelDTO> getAllModelNominalLabelsByModelID(Long modelID, String crisisCode) {
		List<ModelNominalLabelDTO> dtoList = new ArrayList<ModelNominalLabelDTO>();;
		List<ModelNominalLabel> modelNominalLabelList = new ArrayList<ModelNominalLabel>();

		Model model = modelEJB.getById(modelID);
		if (model != null) {
			logger.info("Model is not NULL in getAllModelNominalLabelsByModelID for modelID = "  + modelID + ",crisis code = " + crisisCode);
			Hibernate.initialize(model.getModelFamily());

			try {
				Boolean modelStatus = model.getModelFamily().isIsActive(); //getting model status
				ModelFamily modelFamily = model.getModelFamily();
				Hibernate.initialize(modelFamily.getNominalAttribute());
				Long nominalAttributeId = modelFamily.getNominalAttribute().getNominalAttributeId();
				//Long nominalAttributeId = model.getModelFamily().getNominalAttribute().getNominalAttributeId();

				modelNominalLabelList = this.getAllByCriteria(Restrictions.eq("id.modelId", modelID));
				logger.info("modelNominalLabellist size = " + (modelNominalLabelList != null ? modelNominalLabelList.size() : "null"));								

				for (ModelNominalLabel labelEntity : modelNominalLabelList) {

					//Getting training examples for each label
					int trainingSet = 0;
					NominalLabel nominalLabel = labelEntity.getNominalLabel();

					if (nominalLabel != null && !nominalLabel.getNominalLabelCode().equalsIgnoreCase("null")) {
						try {
							trainingSet = documentEJB.getDocumentCountForNominalLabelAndCrisis(nominalLabel.getNominalLabelId(),crisisCode);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							logger.error("Error in getDocumentCollectionWithNominalLabelData.", e);
						}

					}
					// Deep copying modelNominalLabel to ModelNominalLabelDTO
					Hibernate.initialize(labelEntity.getModel());
					Hibernate.initialize(labelEntity.getNominalLabel());

					ModelNominalLabelDTO dto = new ModelNominalLabelDTO(labelEntity);
					dto.setModelStatus(modelStatus == true ? "RUNNING" : "NOT RUNNING");
					dto.setNominalAttributeId(nominalAttributeId);
					dto.setTrainingDocuments(trainingSet);
					dtoList.add(dto);
				}
			} catch (Exception e) {
				logger.error("Exception occured in getAllModelNominalLabelsByModelID \n\n", e);
				return null;
			}
		}
		return dtoList;
	}

	@Override
	public void saveModelNominalLabel(ModelNominalLabelDTO modelNominalLabel) throws PropertyNotSetException {
		save(modelNominalLabel.toEntity());
	}

	@Override
	public ModelNominalLabelDTO addModelNominalLabel(ModelNominalLabelDTO modelNominalLabel)  {
		try {
			ModelNominalLabel modelLabel = modelNominalLabel.toEntity();
			em.persist(modelLabel);
			em.flush();
			em.refresh(modelLabel);
			return new ModelNominalLabelDTO(modelLabel); 
		} catch (Exception e) {
			logger.error("Error in addModelNominalLabel.", e);
			return null;
		}
	}

	@Override
	public ModelNominalLabelDTO editModelNominalLabel(ModelNominalLabelDTO modelNominalLabel) throws PropertyNotSetException {
		logger.info("Received request for: " + modelNominalLabel.getIdDTO().getNominalLabelId() + ":" + modelNominalLabel.getIdDTO().getModelId());
		try {
			ModelNominalLabel label = modelNominalLabel.toEntity();
			ModelNominalLabel oldLabel = getByCriteria(Restrictions.eq("id", modelNominalLabel.getIdDTO().toEntity()));
			if (oldLabel != null) {
				oldLabel = em.merge(label);
				return (oldLabel != null) ? new ModelNominalLabelDTO(oldLabel) : null;
			} else {
				throw new RuntimeException("Not found");
			}
		} catch (Exception e) {
			logger.error("Exception in merging/updating nominalLabel: " + modelNominalLabel.getIdDTO().getNominalLabelId(), e);
		}
		return null;
	}

	@Override
	public Integer deleteModelNominalLabel(ModelNominalLabelDTO modelNominalLabel) throws PropertyNotSetException {
		if (modelNominalLabel != null) {
			ModelNominalLabel managed = em.merge(modelNominalLabel.toEntity());
			em.remove(managed);
			return 1;
		}
		return 0;
	}

	@Override
	public ModelNominalLabelDTO getModelNominalLabelByID(Long nominalLabelID) throws PropertyNotSetException {
		ModelNominalLabel nb = this.getByCriteria(Restrictions.eq("id.nominalLabelId", nominalLabelID));
		return nb != null ? new ModelNominalLabelDTO(nb) : null;
	}

	@Override
	public Boolean isModelNominalLabelExists(Long nominalLabelID) throws PropertyNotSetException {
		return (getModelNominalLabelByID(nominalLabelID) != null) ? true : false;
	}

	@Override
	public void deleteModelNominalLabelByModelID(Long modelID) {
		List<ModelNominalLabel> modelNominalLabelList = this.getAllByCriteria(Restrictions.eq("id.modelId", modelID));
		for (ModelNominalLabel modelNominalLabel : modelNominalLabelList) {
			delete(modelNominalLabel);
		}
	}
}
