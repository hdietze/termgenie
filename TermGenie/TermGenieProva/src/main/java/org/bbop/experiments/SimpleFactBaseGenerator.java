package org.bbop.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SimpleFactBaseGenerator {

	public static void main(String[] args) throws Exception {
		int termAndRelationCount = 10000;
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(new File("src/main/resources/speed/speed"+termAndRelationCount+".pro")));
		writer.append("%Generated by "+SimpleFactBaseGenerator.class.getName());
		writer.newLine();
		writer.newLine();
		createFactbase(1, termAndRelationCount, writer);
		generateRules(writer);
		writer.close();
	}

	private static void generateRules(BufferedWriter writer) throws IOException {
		writer.newLine();
		writer.newLine();
		writer.append("isID(ID) :- term(ID,Label,TextDef).");
		writer.newLine();
		writer.append("isLabel(Label) :- term(ID,Label,TextDef).");
		writer.newLine();
		writer.append("isTextDef(TextDef) :- term(ID,Label,TextDef).");
		writer.newLine();
		writer.newLine();
		writer.append("isTransitive(subClassOf).");
		writer.newLine();
		writer.append("isTransitive(superClassOf).");
		writer.newLine();
		writer.newLine();
		writer.append("isTTriple(A, Relation, B) :- isTriple(A, Relation, B), isTransitive(Relation).");
		writer.newLine();
		writer.append("isTTriple(A, Relation, C) :- isTriple(A, Relation, B), isTTriple(B, Relation, C), isTransitive(Relation).");
		writer.newLine();
		writer.newLine();
		writer.append("isTriple(Subject, superClassOf, Object) :- isTriple(Object, subClassOf, Subject).");
		writer.newLine();
		writer.newLine();
	}

	public static void createFactbase(int startCount, int endCount, BufferedWriter writer)
			throws IOException {
		
		for (int i = startCount; i <= endCount; i++) {
			String id = Integer.toString(i);
			writer.append("term('ID:" + id + "','Label for " + id + "','Textual defintion for "
					+ id + "').");
			writer.newLine();
			if (i > 1) {
				String parent = Integer.toString(i / 2);
				writer.append("isTriple('ID:" + id + "', subClassOf,'ID:" + parent + "').");
				writer.newLine();
			}

		}
	}
}

