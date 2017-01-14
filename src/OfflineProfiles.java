/**
 * OfflineProfiles.java: Representation of OfflineProfiles.xml
 * Copyright (C) 2017 Christian Schrötter <cs@fnx.li>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 */

import java.io.File;
import java.io.FileNotFoundException;

import java.lang.IndexOutOfBoundsException;

import java.util.ArrayList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OfflineProfiles
{
	final private static String XML_TAG_DEFAULT = "DefaultProfile";
	final private static String XML_TAG_PROFILE = "OfflineProfile";
	final private static String XML_TAG_GHOSTS  = "TrainingGhosts";
	final private static String XML_TAG_GHOST   = "GhostDataPair";
	final private static String XML_TAG_NICK    = "Nickname";

	private File     file     = null;
	private Document document = null;
	private boolean  changed  = false;
	private int      profile  = 0;

	private NodeList                OfflineProfiles;
	private Element                 OfflineProfile;
	private Element                 DefaultProfile;
	private Node                    TrainingNode;
	private Element                 TrainingElement;
	private NodeList                TrainingGhosts;
	private ArrayList<GhostElement> GhostElements;

	public OfflineProfiles(String xmlstring) throws Exception
	{
		this.file = null;
		this.document = FNX.getDOMDocument(xmlstring);
		this.postParsing();
	}

	public OfflineProfiles(File xmlfile) throws Exception
	{
		this.checkFile(xmlfile);
		this.file = xmlfile;
		this.reload();
	}

	private void checkFile(File xmlfile) throws Exception
	{
		if(xmlfile == null || !xmlfile.exists() || !xmlfile.isFile())
		{
			throw new FileNotFoundException(xmlfile.getAbsolutePath());
		}
	}

	public void updateFile(File xmlfile) throws Exception
	{
		if(this.file == null)
		{
			throw new IllegalStateException("OfflineProfiles not initialized with File; updateFile() not possible");
		}

		this.checkFile(xmlfile);
		this.file = xmlfile;
	}

	public void reload() throws Exception
	{
		if(this.file == null)
		{
			throw new IllegalStateException("OfflineProfiles not initialized with File; reload() not possible");
		}

		this.changed = false;
		this.document = FNX.getDOMDocument(this.file);
		this.postParsing();
	}

	private void postParsing() throws Exception
	{
		this.document.setXmlStandalone(true);

		this.TrainingElement  = null;
		this.OfflineProfile  = null;

		// xsi:type="GameOfflineProfile"
		this.OfflineProfiles = document.getElementsByTagName(this.XML_TAG_PROFILE);
		NodeList DefaultProfiles = document.getElementsByTagName(this.XML_TAG_DEFAULT);

		if(this.getProfileCount() == 0 && DefaultProfiles.getLength() == 0)
		{
			throw new ProfileException(String.format("Missing <%s> and <%s> tags", XML_TAG_PROFILE, XML_TAG_DEFAULT));
		}
		else if(DefaultProfiles.getLength() > 1)
		{
			throw new ProfileException(String.format("Too many <%s> tags", XML_TAG_DEFAULT));
		}

		if(DefaultProfiles.getLength() > 0)
		{
			this.DefaultProfile = (Element) DefaultProfiles.item(0);
		}
		else
		{
			this.DefaultProfile = null;
		}

		this.selectProfile(0);
	}

	public int defaultProfile()
	{
		if(this.DefaultProfile == null)
		{
			return -1;
		}
		else if(this.getProfileCount() == 0)
		{
			return 0;
		}

		return this.getProfileCount() - 1;
	}

	public int getProfileCount()
	{
		if(this.OfflineProfiles == null)
		{
			throw new IndexOutOfBoundsException("OfflineProfiles == null");
		}

		return this.OfflineProfiles.getLength() + ((this.DefaultProfile != null) ? 1 : 0);
	}

	public int getGhostCount()
	{
		if(this.TrainingGhosts != null)
		{
			return this.TrainingGhosts.getLength();
		}

		return 0;
	}

	public GhostElement getGhost(int index)
	{
		if(index >= getGhostCount())
		{
			throw new IndexOutOfBoundsException(String.format("Ghost #%d", index));
		}

		return this.GhostElements.get(index);
	}

	public int[] getGhostsByCondition(String track, int weather)
	{
		track = track.toLowerCase();
		ArrayList<Integer> ghosts = new ArrayList<Integer>();

		for(int i = 0; i < this.getGhostCount(); i++)
		{
			GhostElement ghost = this.getGhost(i);

			if(ghost.getTrack().toLowerCase().equals(track) && ghost.getWeather() == weather)
			{
				ghosts.add(i);
			}
		}

		return ghosts.stream().mapToInt(i->i).toArray();
	}

	public int[] getGhostsByCondition(GhostElement ghost)
	{
		return this.getGhostsByCondition(ghost.getTrack(), ghost.getWeather());
	}

	public GhostElement[][] getAllGhosts()
	{
		return this.getAllGhosts(false);
	}

	public GhostElement[][] getAllGhosts(boolean warn)
	{
		String[] tracks = gmHelper.getTracks(true);
		int[] weathers = gmHelper.getWeatherIDs();

		GhostElement result[][] = new GhostElement[tracks.length][weathers.length];

		for(int t = 0; t < tracks.length; t++)
		{
			for(int w = 0; w < weathers.length; w++)
			{
				int[] ghosts = this.getGhostsByCondition(tracks[t], weathers[w]);

				if(ghosts.length > 1 && warn)
				{
					return null;
				}
				else if(ghosts.length > 0)
				{
					result[t][w] = this.getGhost(ghosts[0]);
				}
				else
				{
					result[t][w] = null;
				}
			}
		}

		return result;
	}

	public void deleteGhost(int index) throws Exception
	{
		this.changed = true;
		this.GhostElements.remove(index);
		Element GhostElement = (Element) this.TrainingGhosts.item(index);
		GhostElement.getParentNode().removeChild(GhostElement);

		if(this.GhostElements.size() != this.TrainingGhosts.getLength())
		{
			throw new Exception(String.format("GhostElements(%d) != TrainingGhosts(%d)", this.GhostElements.size(), this.TrainingGhosts.getLength()));
		}
	}

	public int addGhost(String ghost) throws Exception
	{
		return this.addGhost(new GhostElement(ghost));
	}

	public int addGhost(GhostElement ghost) throws Exception
	{
		this.changed = true;
		Node importedNode = this.document.importNode(ghost.getElement(), false);
		this.TrainingNode.appendChild(importedNode);
		ghost = new GhostElement(importedNode);
		this.GhostElements.add(ghost);

		if(this.GhostElements.size() != this.TrainingGhosts.getLength())
		{
			throw new Exception(String.format("GhostElements(%d) != TrainingGhosts(%d)", this.GhostElements.size(), this.TrainingGhosts.getLength()));
		}

		return this.getGhostCount() - 1;
	}

	public String[] getProfiles() throws Exception
	{
		String[] profiles = new String[this.getProfileCount()];

		for(int i = 0; i < this.getProfileCount(); i++)
		{
			Element profile;

			if(i == this.defaultProfile())
			{
				profile = (Element) DefaultProfile;
			}
			else
			{
				profile = (Element) OfflineProfiles.item(i);
			}

			NodeList nick = profile.getElementsByTagName(this.XML_TAG_NICK);

			if(nick.getLength() > 0)
			{
				Element nickname = (Element) nick.item(0);
				profiles[i] = nickname.getTextContent();
			}
			else
			{
				throw new ProfileException(String.format("No <%s> tag in profile #%d", this.XML_TAG_NICK, i));
			}
		}

		return profiles;
	}

	public void selectProfile(int index) throws Exception
	{
		if(index >= this.getProfileCount())
		{
			throw new IndexOutOfBoundsException(String.format("OfflineProfile #%d", index));
		}

		this.profile = index;
		this.GhostElements = null;
		this.TrainingElement = null;

		if(this.profile == this.defaultProfile())
		{
			this.OfflineProfile = (Element) DefaultProfile;
		}
		else
		{
			this.OfflineProfile = (Element) OfflineProfiles.item(this.profile);
		}

		NodeList GhostNodes = this.OfflineProfile.getElementsByTagName(this.XML_TAG_GHOSTS);
		this.GhostElements = new ArrayList<GhostElement>(0);

		if(GhostNodes.getLength() > 0)
		{
			this.TrainingNode = GhostNodes.item(0);
			this.TrainingElement = (Element) this.TrainingNode;
			this.TrainingGhosts = this.TrainingElement.getElementsByTagName(this.XML_TAG_GHOST);

			if(this.getGhostCount() > 0)
			{
				this.GhostElements = new ArrayList<GhostElement>(this.getGhostCount());

				for(int i = 0; i < this.getGhostCount(); i++)
				{
					try
					{
						// Damit ein Geist nur einmal verarbeitet wird, werden alle bereits hier eingelesen.
						// Somit erfolgt nur noch beim Profilwechsel eine aufwändige erneute Verarbeitung.
						// Das ist allerdings gewollt, damit nicht unnötig Arbeitsspeicher belegt wird.
						this.GhostElements.add(i, new GhostElement((Element) this.TrainingGhosts.item(i)));
					}
					catch(GhostException e)
					{
						throw new GhostException(i, e.getMessage());
					}
				}
			}
		}
	}

	public String toString()
	{
		String XML;

		try
		{
			this.document.getDocumentElement().normalize();
			XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//text()[normalize-space(.) = '']");
			NodeList blankTextNodes = (NodeList) xpath.evaluate(this.document, XPathConstants.NODESET);

			for(int i = 0; i < blankTextNodes.getLength(); i++)
			{
				blankTextNodes.item(i).getParentNode().removeChild(blankTextNodes.item(i));
			}

			return FNX.getWinNL(FNX.getStringFromDOM(this.document, true));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public boolean changed()
	{
		return this.changed;
	}

	public void saved()
	{
		this.changed = false;
	}
}
