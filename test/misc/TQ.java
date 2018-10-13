/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package misc;

import java.io.File;
import uk.co.agena.minerva.util.io.FileHandler;

/**
 *
 * @author Eugene Dementiev
 */
public class TQ {
	public static void main(String[] args) throws Exception {
		String s = "D:\\Dropbox\\Agena\\updates\\data format\\\"D:\\Dropbox\\Agena\\updates\\data format\\created_from_scratch (saved f desk).cmp\"";
		File fileSelected = new File(s);
		System.out.println(fileSelected.getAbsolutePath());
		System.out.println(fileSelected.getPath());
		System.out.println(fileSelected.getName());
		
		// Strip enclosing quotes
		if (fileSelected.getAbsolutePath().matches(".*['\"].*['\"]$")){
			fileSelected = new File(fileSelected.getAbsolutePath().replaceFirst(".*['\"](.*)['\"]$", "$1"));
		}
		
		System.out.println(fileSelected.getAbsolutePath());
		System.out.println(fileSelected.getPath());
		
	}
}
