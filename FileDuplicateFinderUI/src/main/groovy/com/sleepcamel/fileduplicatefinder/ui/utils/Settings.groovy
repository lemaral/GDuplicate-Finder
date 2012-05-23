package com.sleepcamel.fileduplicatefinder.ui.utils

import org.apache.commons.beanutils.BeanUtils;

@Singleton
class Settings implements Serializable {

	static final long serialVersionUID = -6618469841127325812L;
	
	def lastNetworkAuthModels
	
	def getLastNetworkDrivesAuthModels(){
		lastNetworkAuthModels?:[]
	}
	
	def getMountedNetworkDrivesAuthModels(){
		getLastNetworkDrivesAuthModels().findAll { it.isMounted }.collect { it.auth }
	}
	
	def load(){
		def file = new File("settings.dat")
		if ( file.exists() ){
			FileInputStream fis = new FileInputStream(file)
			fis.withObjectInputStream { ois ->
				BeanUtils.copyProperties(instance, ois.readObject());
				ois.close()
			}
		}
	}
	
	def save(){
		FileOutputStream fos = new FileOutputStream(new File("settings.dat"))
		fos.withObjectOutputStream { oos ->
			oos.writeObject(this);
			oos.close()
		}
	}
}
